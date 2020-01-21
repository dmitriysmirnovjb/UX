// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.diagnostic.LoadingState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ComponentWithBrowseButton<Comp extends JComponent> extends JPanel implements Disposable {
  private final Comp myComponent;
  private final FixedSizeButton myBrowseButton;
  private boolean myButtonEnabled = true;

  @ApiStatus.Internal
  public static boolean isUseInlineBrowserButton() {
    return !LoadingState.COMPONENTS_REGISTERED.isOccurred() || Experiments.getInstance().isFeatureEnabled("inline.browse.button");
  }

  public ComponentWithBrowseButton(@NotNull Comp component, @Nullable ActionListener browseActionListener) {
    super(new BorderLayout(SystemInfo.isMac || StartupUiUtil.isUnderDarcula() ? 0 : 2, 0));

    myComponent = component;
    // required! otherwise JPanel will occasionally gain focus instead of the component
    setFocusable(false);
    boolean inlineBrowseButton = myComponent instanceof ExtendableTextComponent && isUseInlineBrowserButton();
    if (inlineBrowseButton) {
      ((ExtendableTextComponent)myComponent).addExtension(ExtendableTextComponent.Extension.create(
        getDefaultIcon(), getHoveredIcon(), getIconTooltip(), this::notifyActionListeners));
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          notifyActionListeners();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), myComponent);
    }
    add(myComponent, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(myComponent);
    if (browseActionListener != null) {
      myBrowseButton.addActionListener(browseActionListener);
    }
    if (!inlineBrowseButton) {
      add(myBrowseButton, BorderLayout.EAST);
    }

    myBrowseButton.setToolTipText(getIconTooltip());
    // FixedSizeButton isn't focusable but it should be selectable via keyboard.
    if (ApplicationManager.getApplication() != null) {  // avoid crash at design time
      new MyDoClickAction(myBrowseButton).registerShortcut(myComponent);
    }
    if (ScreenReader.isActive()) {
      myBrowseButton.setFocusable(true);
      myBrowseButton.getAccessibleContext().setAccessibleName("Browse");
    }
  }

  @NotNull
  protected Icon getDefaultIcon() {
    return AllIcons.General.OpenDisk;
  }

  @NotNull
  protected Icon getHoveredIcon() {
    return AllIcons.General.OpenDiskHover;
  }

  @NotNull
  protected String getIconTooltip() {
    return UIBundle.message("component.with.browse.button.browse.button.tooltip.text") + " (" +
           KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)) + ")";
  }

  private void notifyActionListeners() {
    ActionEvent event = new ActionEvent(myComponent, ActionEvent.ACTION_PERFORMED, "action");
    for (ActionListener listener: myBrowseButton.getActionListeners()) listener.actionPerformed(event);
  }

  @NotNull
  public final Comp getChildComponent() {
    return myComponent;
  }

  public void setTextFieldPreferredWidth(final int charCount) {
    JComponent comp = getChildComponent();
    Dimension size = GuiUtils.getSizeByChars(charCount, comp);
    comp.setPreferredSize(size);
    Dimension preferredSize = myBrowseButton.getPreferredSize();

    boolean keepHeight = UIUtil.isUnderWin10LookAndFeel();
    preferredSize.setSize(size.width + preferredSize.width + 2,
                          keepHeight ? preferredSize.height : preferredSize.height + 2);

    setPreferredSize(preferredSize);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myBrowseButton.setEnabled(enabled && myButtonEnabled);
    myComponent.setEnabled(enabled);
  }

  public void setButtonEnabled(boolean buttonEnabled) {
    myButtonEnabled = buttonEnabled;
    setEnabled(isEnabled());
  }

  public void setButtonIcon(@NotNull Icon icon) {
    myBrowseButton.setIcon(icon);
    myBrowseButton.setDisabledIcon(IconLoader.getDisabledIcon(icon));
  }

  /**
   * Adds specified {@code listener} to the browse button.
   */
  public void addActionListener(ActionListener listener){
    myBrowseButton.addActionListener(listener);
  }

  public void removeActionListener(ActionListener listener) {
    myBrowseButton.removeActionListener(listener);
  }

  public void addBrowseFolderListener(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                      @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<? super Comp> accessor) {
    addActionListener(new BrowseFolderActionListener<>(title, description, this, project, fileChooserDescriptor, accessor));
  }

  /**
   * @deprecated use {@link #addBrowseFolderListener(String, String, Project, FileChooserDescriptor, TextComponentAccessor)} instead
   */
  @Deprecated
  public void addBrowseFolderListener(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                      @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<? super Comp> accessor, boolean autoRemoveOnHide) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor);
  }

  /**
   * @deprecated use {@link #addActionListener(ActionListener)} instead
   */
  @Deprecated
  public void addBrowseFolderListener(@Nullable Project project, final BrowseFolderActionListener<Comp> actionListener) {
    addActionListener(actionListener);
  }

  @Override
  public void dispose() {
    ActionListener[] listeners = myBrowseButton.getActionListeners();
    for (ActionListener listener : listeners) {
      myBrowseButton.removeActionListener(listener);
    }
  }

  public FixedSizeButton getButton() {
    return myBrowseButton;
  }

  /**
   * Do not use this class directly it is public just to hack other implementation of controls similar to TextFieldWithBrowseButton.
   */
  public static final class MyDoClickAction extends DumbAwareAction {
    private final FixedSizeButton myBrowseButton;
    public MyDoClickAction(FixedSizeButton browseButton) {
      myBrowseButton = browseButton;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myBrowseButton.isVisible() && myBrowseButton.isEnabled());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e){
      myBrowseButton.doClick();
    }

    public void registerShortcut(JComponent textField) {
      ShortcutSet shiftEnter = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
      registerCustomShortcutSet(shiftEnter, textField);
      myBrowseButton.setToolTipText(KeymapUtil.getShortcutsText(shiftEnter.getShortcuts()));
    }

    public static void addTo(FixedSizeButton browseButton, JComponent aComponent) {
      new MyDoClickAction(browseButton).registerShortcut(aComponent);
    }
  }

  public static class BrowseFolderActionListener<T extends JComponent> extends BrowseFolderRunnable <T> implements ActionListener {
    public BrowseFolderActionListener(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                      @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                      @Nullable ComponentWithBrowseButton<T> textField,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<? super T> accessor) {
      super(title, description, project, fileChooserDescriptor, textField != null ? textField.getChildComponent() : null, accessor);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      run();
    }
  }

  @Override
  public final void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() ->
      IdeFocusManager.getGlobalInstance().requestFocus(myComponent, true));
  }

  @SuppressWarnings("deprecation")
  @Override
  public final void setNextFocusableComponent(Component aComponent) {
    super.setNextFocusableComponent(aComponent);
    myComponent.setNextFocusableComponent(aComponent);
  }

  private KeyEvent myCurrentEvent = null;

  @Override
  protected final boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (condition == WHEN_FOCUSED && myCurrentEvent != e) {
      try {
        myCurrentEvent = e;
        myComponent.dispatchEvent(e);
      }
      finally {
        myCurrentEvent = null;
      }
    }
    if (e.isConsumed()) return true;
    return super.processKeyBinding(ks, e, condition, pressed);
  }
  /**
   * @deprecated use {@link #addActionListener(ActionListener)} instead
   */
  @Deprecated
  public void addBrowseFolderListener(@Nullable Project project, final BrowseFolderActionListener<Comp> actionListener, boolean autoRemoveOnHide) {
    addActionListener(actionListener);
  }

}
