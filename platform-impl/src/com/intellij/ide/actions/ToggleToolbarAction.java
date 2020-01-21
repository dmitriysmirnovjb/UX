// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author gregsh
 */
public final class ToggleToolbarAction extends ToggleAction implements DumbAware {
  @NotNull
  public static DefaultActionGroup createToggleToolbarGroup(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    return new DefaultActionGroup(new OptionsGroup(toolWindow),
                                  createToolWindowAction(toolWindow, PropertiesComponent.getInstance(project)));
  }

  @NotNull
  public static ToggleToolbarAction createAction(@NotNull String id,
                                                 @NotNull PropertiesComponent properties,
                                                 @NotNull Supplier<? extends Iterable<JComponent>> components) {
    return new ToggleToolbarAction(properties, getShowToolbarProperty(id), components);
  }

  @NotNull
  public static ToggleToolbarAction createToolWindowAction(@NotNull ToolWindow toolWindow, @NotNull PropertiesComponent properties) {
    toolWindow.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentAdded(@NotNull ContentManagerEvent event) {
        JComponent component = event.getContent().getComponent();
        setToolbarVisible(Collections.singletonList(component), isToolbarVisible(toolWindow, properties));

        // support nested content managers, e.g. RunnerLayoutUi as content component
        ContentManager contentManager = component instanceof DataProvider ? PlatformDataKeys.CONTENT_MANAGER.getData((DataProvider)component) : null;
        if (contentManager != null) {
          contentManager.addContentManagerListener(this);
        }
      }
    });
    return new ToggleToolbarAction(properties, getShowToolbarProperty(toolWindow), () -> {
      return Collections.singletonList(toolWindow.getContentManager().getComponent());
    });
  }

  public static void setToolbarVisible(@NotNull ToolWindow toolWindow,
                                       @NotNull PropertiesComponent properties,
                                       @Nullable Boolean visible) {
    boolean state = visible == null ? isToolbarVisible(toolWindow, properties) : visible;
    setToolbarVisibleImpl(getShowToolbarProperty(toolWindow), properties, Collections.singletonList(toolWindow.getComponent()), state);
  }

  public static void setToolbarVisible(@NotNull String id,
                                       @NotNull PropertiesComponent properties,
                                       @NotNull Iterable<? extends JComponent> components,
                                       @Nullable Boolean visible) {
    boolean state = visible == null ? isToolbarVisible(id, properties) : visible;
    setToolbarVisibleImpl(getShowToolbarProperty(id), properties, components, state);
  }

  public static void setToolbarVisible(@NotNull Iterable<? extends JComponent> roots, boolean state) {
    for (ActionToolbar toolbar : iterateToolbars(roots)) {
      JComponent c = toolbar.getComponent();
      c.setVisible(state);
      Container parent = c.getParent();
      if (parent instanceof EditorHeaderComponent) {
        parent.setVisible(state);
      }
    }
  }

  public static boolean isToolbarVisible(@NotNull String property, @NotNull PropertiesComponent properties) {
    return isSelectedImpl(properties, getShowToolbarProperty(property));
  }

  public static boolean isToolbarVisible(@NotNull ToolWindow toolWindow, @NotNull PropertiesComponent properties) {
    return isSelectedImpl(properties, getShowToolbarProperty(toolWindow));
  }

  private final PropertiesComponent myPropertiesComponent;
  private final String myProperty;
  private final Supplier<? extends Iterable<JComponent>> myProducer;

  private ToggleToolbarAction(@NotNull PropertiesComponent propertiesComponent,
                              @NotNull String property,
                              @NotNull Supplier<? extends Iterable<JComponent>> producer) {
    super("Show Toolbar");
    myPropertiesComponent = propertiesComponent;
    myProperty = property;
    myProducer = producer;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    boolean hasToolbars = iterateToolbars(myProducer.get()).iterator().hasNext();
    e.getPresentation().setVisible(hasToolbars);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isSelected();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setToolbarVisibleImpl(myProperty, myPropertiesComponent, myProducer.get(), state);
  }

  static void setToolbarVisibleImpl(@NotNull String property,
                                    @NotNull PropertiesComponent propertiesComponent,
                                    @NotNull Iterable<? extends JComponent> components,
                                    boolean visible) {
    propertiesComponent.setValue(property, String.valueOf(visible), String.valueOf(true));
    setToolbarVisible(components, visible);
  }

  boolean isSelected() {
    return isSelectedImpl(myPropertiesComponent, myProperty);
  }

  static boolean isSelectedImpl(@NotNull PropertiesComponent properties, @NotNull String property) {
    return properties.getBoolean(property, true);
  }

  @NotNull
  static String getShowToolbarProperty(@NotNull ToolWindow window) {
    return getShowToolbarProperty("ToolWindow" + window.getStripeTitle());
  }

  @NotNull
  static String getShowToolbarProperty(@NotNull String s) {
    return s + ".ShowToolbar";
  }

  @NotNull
  private static Iterable<ActionToolbar> iterateToolbars(Iterable<? extends JComponent> roots) {
    return UIUtil.uiTraverser(null).withRoots(roots).preOrderDfsTraversal().filter(ActionToolbar.class);
  }

  private static class OptionsGroup extends ActionGroup implements DumbAware {

    private final ToolWindow myToolWindow;

    OptionsGroup(ToolWindow toolWindow) {
      super(IdeBundle.message("group.view.options"), true);
      myToolWindow = toolWindow;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(!ActionGroupUtil.isGroupEmpty(this, e, LaterInvocator.isInModalContext()));
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      ContentManager contentManager = myToolWindow.getContentManager();
      Content selectedContent = contentManager.getSelectedContent();
      JComponent contentComponent = selectedContent != null ? selectedContent.getComponent() : null;
      if (contentComponent == null || e == null) return EMPTY_ARRAY;
      List<AnAction> result = new SmartList<>();
      for (final ActionToolbar toolbar : iterateToolbars(Collections.singletonList(contentComponent))) {
        JComponent c = toolbar.getComponent();
        if (c.isVisible() || !c.isValid()) continue;
        if (!result.isEmpty() && !(ContainerUtil.getLastItem(result) instanceof Separator)) {
          result.add(Separator.getInstance());
        }

        List<AnAction> actions = toolbar.getActions();
        for (AnAction action : actions) {
          if (action instanceof ToggleAction && !result.contains(action) &&
              ActionGroupUtil.isActionEnabledAndVisible(action, e, LaterInvocator.isInModalContext())) {
            result.add(action);
          }
          else if (action instanceof Separator) {
            if (!result.isEmpty() && !(ContainerUtil.getLastItem(result) instanceof Separator)) {
              result.add(Separator.getInstance());
            }
          }
        }
      }
      boolean popup = ContainerUtil.count(result, it -> !(it instanceof Separator)) > 3;
      setPopup(popup);
      if (!popup && !result.isEmpty()) result.add(Separator.getInstance());
      return result.toArray(AnAction.EMPTY_ARRAY);
    }
  }
}
