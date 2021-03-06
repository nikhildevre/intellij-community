// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerContentLoader
import com.intellij.openapi.vcs.impl.LineStatusTrackerContentLoader.ContentInfo
import com.intellij.openapi.vcs.impl.LineStatusTrackerContentLoader.TrackerContent
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.index.vfs.GitIndexVirtualFileCache
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import java.nio.charset.Charset

class GitStageLineStatusTrackerProvider : LineStatusTrackerContentLoader {
  private val LOG = Logger.getInstance(GitStageLineStatusTrackerProvider::class.java)

  override fun isMyTracker(tracker: LocalLineStatusTracker<*>): Boolean = tracker is GitStageLineStatusTracker

  override fun isTrackedFile(project: Project, file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem) return false
    if (!isStageAvailable(project)) return false
    if (!stageLineStatusTrackerRegistryOption().asBoolean()) return false

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file)
    return repository != null
  }

  override fun createTracker(project: Project, file: VirtualFile): LocalLineStatusTracker<*>? {
    val status = GitStageTracker.getInstance(project).status(file) ?: return null

    if (!status.isTracked() ||
        !status.has(ContentVersion.STAGED) ||
        !status.has(ContentVersion.LOCAL)) return null

    val root = VcsUtil.getVcsRootFor(project, file) ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null

    return GitStageLineStatusTracker(project, file, document)
  }


  override fun getContentInfo(project: Project, file: VirtualFile): ContentInfo? {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return null
    return StagedContentInfo(repository.currentRevision, file.charset, file)
  }

  override fun shouldBeUpdated(oldInfo: ContentInfo?, newInfo: ContentInfo): Boolean {
    newInfo as StagedContentInfo
    return oldInfo == null ||
           oldInfo !is StagedContentInfo ||
           oldInfo.currentRevision != newInfo.currentRevision ||
           oldInfo.charset != newInfo.charset
  }

  override fun loadContent(project: Project, info: ContentInfo): TrackerContent? {
    info as StagedContentInfo

    val file = info.virtualFile
    val filePath = VcsUtil.getFilePath(file)
    val status = GitStageTracker.getInstance(project).status(file) ?: return null

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return null

    val indexFileCache = project.service<GitIndexVirtualFileCache>()
    val indexFile = indexFileCache.get(repository.root, status.path(ContentVersion.STAGED))
    val indexDocument = runReadAction { FileDocumentManager.getInstance().getDocument(indexFile) } ?: return null

    if (!status.has(ContentVersion.HEAD)) return StagedTrackerContent("", indexDocument)

    try {
      val bytes = GitFileUtils.getFileContent(project, repository.root, GitUtil.HEAD,
                                              VcsFileUtil.relativePath(repository.root, status.path(ContentVersion.HEAD)))
      val charset: Charset = DiffContentFactoryImpl.guessCharset(project, bytes, filePath)
      val headContent = CharsetToolkit.decodeString(bytes, charset)
      val correctedText = StringUtil.convertLineSeparators(headContent)

      return StagedTrackerContent(correctedText, indexDocument)
    } catch (e : VcsException) {
      LOG.warn("Can't load base revision content for ${file.path} with status $status", e)
      return null
    }
  }

  override fun setLoadedContent(tracker: LocalLineStatusTracker<*>, content: TrackerContent) {
    tracker as GitStageLineStatusTracker
    content as StagedTrackerContent
    tracker.setBaseRevision(content.vcsContent, content.stagedDocument)
  }

  override fun handleLoadingError(tracker: LocalLineStatusTracker<*>) {
    tracker as GitStageLineStatusTracker
    tracker.dropBaseRevision()
  }

  private class StagedContentInfo(val currentRevision: String?, val charset: Charset, val virtualFile: VirtualFile) : ContentInfo
  private class StagedTrackerContent(val vcsContent: CharSequence, val stagedDocument: Document) : TrackerContent
}