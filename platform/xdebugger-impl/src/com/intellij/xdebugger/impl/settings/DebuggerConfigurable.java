/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable implements SearchableConfigurable.Parent {
  public static final String DISPLAY_NAME = XDebuggerBundle.message("debugger.configurable.display.name");

  static final Configurable[] EMPTY_CONFIGURABLES = new Configurable[0];

  private Configurable myRootConfigurable;
  private Configurable[] myChildren;

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getHelpTopic() {
    return myRootConfigurable != null ? myRootConfigurable.getHelpTopic() : null;
  }

  @Override
  public Configurable[] getConfigurables() {
    compute();

    if (myChildren.length == 0 && myRootConfigurable instanceof SearchableConfigurable.Parent) {
      return ((Parent)myRootConfigurable).getConfigurables();
    }
    else {
      return myChildren;
    }
  }

  private void compute() {
    if (myChildren != null) {
      return;
    }

    List<DebuggerSettingsPanelProvider> providers = DebuggerConfigurableProvider.getSortedProviders();

    List<Configurable> configurables = new ArrayList<Configurable>();
    configurables.add(new DataViewsConfigurable());

    Configurable rootConfigurable = null;
    for (DebuggerSettingsPanelProvider provider : providers) {
      configurables.addAll(provider.getConfigurables());
      Configurable aRootConfigurable = provider.getRootConfigurable();
      if (aRootConfigurable != null) {
        if (rootConfigurable != null) {
          configurables.add(aRootConfigurable);
        }
        else {
          rootConfigurable = aRootConfigurable;
        }
      }
    }

    if (configurables.isEmpty() && rootConfigurable == null) {
      myChildren = EMPTY_CONFIGURABLES;
    }
    else if (rootConfigurable == null && configurables.size() == 1) {
      myRootConfigurable = configurables.get(0);
      myChildren = EMPTY_CONFIGURABLES;
    }
    else {
      myChildren = configurables.toArray(new Configurable[configurables.size()]);
      myRootConfigurable = rootConfigurable;
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      support.getSettingsPanelProvider().apply();
    }
    if (myRootConfigurable != null) {
      myRootConfigurable.apply();
    }
  }

  @Override
  public boolean hasOwnContent() {
    compute();
    return myRootConfigurable != null;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Override
  public JComponent createComponent() {
    compute();
    return myRootConfigurable != null ? myRootConfigurable.createComponent() : null;
  }

  @Override
  public boolean isModified() {
    return myRootConfigurable != null && myRootConfigurable.isModified();
  }

  @Override
  public void reset() {
    if (myRootConfigurable != null) {
      myRootConfigurable.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    if (myRootConfigurable != null) {
      myRootConfigurable.disposeUIResources();
    }
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "project.propDebugger";
  }
}
