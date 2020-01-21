// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class CommonActionsPanel extends JPanel {
  private final ActionToolbarPosition myPosition;
  private final ActionToolbar myToolbar;

  public enum Buttons {
    ADD, REMOVE, EDIT,  UP, DOWN;

    public static final Buttons[] ALL = {ADD, REMOVE, EDIT,  UP, DOWN};

    public Icon getIcon() {
      switch (this) {
        case ADD:    return IconUtil.getAddIcon();
        case EDIT:   return IconUtil.getEditIcon();
        case REMOVE: return IconUtil.getRemoveIcon();
        case UP:     return IconUtil.getMoveUpIcon();
        case DOWN:   return IconUtil.getMoveDownIcon();
      }
      return null;
    }

    MyActionButton createButton(final Listener listener, String name, Icon icon) {
      String buttonName = name == null ? StringUtil.capitalize(StringUtil.toLowerCase(name())) : name;
      switch (this) {
        case ADD: return new AddButton(listener, buttonName, icon);
        case REMOVE: return new RemoveButton(listener, buttonName, icon);
        case EDIT: return new EditButton(listener, buttonName, icon);
        case UP: return new UpButton(listener, buttonName, icon);
        case DOWN: return new DownButton(listener, buttonName, icon);
      }
      throw new IllegalStateException("can't reach this");
    }

    public String getText() {
      return StringUtil.capitalize(StringUtil.toLowerCase(name()));
    }
  }

  public interface Listener {
    default void doAdd() {
    }

    default void doRemove() {
    }

    default void doUp() {
    }

    default void doDown() {
    }

    default void doEdit() {
    }
  }

  private final Map<Buttons, MyActionButton> myButtons = new HashMap<>();
  private final AnActionButton[] myActions;
  private EnumMap<Buttons, ShortcutSet> myCustomShortcuts;

  CommonActionsPanel(ListenerFactory factory, @Nullable JComponent contextComponent, ActionToolbarPosition position,
                     AnActionButton @Nullable [] additionalActions, @Nullable Comparator<AnActionButton> buttonComparator,
                     String addName, String removeName, String moveUpName, String moveDownName, String editName,
                     Icon addIcon, Buttons... buttons) {
    super(new BorderLayout());
    myPosition = position;
    final Listener listener = factory.createListener(this);
    AnActionButton[] actions = new AnActionButton[buttons.length + (additionalActions == null ? 0 : additionalActions.length)];
    for (int i = 0; i < buttons.length; i++) {
      Buttons button = buttons[i];
      String name = null;
      switch (button) {
        case ADD:    name = addName;      break;
        case EDIT:   name = editName;     break;
        case REMOVE: name = removeName;   break;
        case UP:     name = moveUpName;   break;
        case DOWN:   name = moveDownName; break;
      }
      final MyActionButton b = button.createButton(listener, name, button == Buttons.ADD && addIcon != null ? addIcon : button.getIcon());
      actions[i] = b;
      myButtons.put(button, b);
    }
    if (additionalActions != null && additionalActions.length > 0) {
      int i = buttons.length;
      for (AnActionButton button : additionalActions) {
        actions[i++] = button;
      }
    }
    myActions = actions;
    for (AnActionButton action : actions) {
      action.setContextComponent(contextComponent);
    }
    if (buttonComparator != null) {
      Arrays.sort(myActions, buttonComparator);
    }
    ArrayList<AnAction> toolbarActions = ContainerUtil.newArrayList(myActions);
    for (int i = 0; i < toolbarActions.size(); i++) {
        if (toolbarActions.get(i) instanceof AnActionButton.CheckedAnActionButton) {
          toolbarActions.set(i, ((AnActionButton.CheckedAnActionButton)toolbarActions.get(i)).getDelegate());
        }
    }

    final ActionManagerEx mgr = (ActionManagerEx)ActionManager.getInstance();
    myToolbar = mgr.createActionToolbar("ToolbarDecorator",
                                        new DefaultActionGroup(toolbarActions.toArray(AnAction.EMPTY_ARRAY)),
                                        position == ActionToolbarPosition.BOTTOM || position == ActionToolbarPosition.TOP);
    myToolbar.getComponent().setBorder(null);
    add(myToolbar.getComponent(), BorderLayout.CENTER);
  }

  @NotNull
  public ActionToolbar getToolbar() {
    return myToolbar;
  }

  public void setToolbarLabel(JComponent label, ActionToolbarPosition position) {
    removeAll();
    add(label, ToolbarDecorator.getPlacement(position));
    if (position == ActionToolbarPosition.LEFT) add(myToolbar.getComponent(), BorderLayout.EAST);
    else if (position == ActionToolbarPosition.RIGHT) add(myToolbar.getComponent(), BorderLayout.WEST);
    else add(myToolbar.getComponent(), BorderLayout.CENTER);
  }

  public AnActionButton getAnActionButton(Buttons button) {
    return myButtons.get(button);
  }

  @Override
  public void addNotify() {
    if (getBackground() != null && !getBackground().equals(UIUtil.getPanelBackground())) {
      SwingUtilities.updateComponentTreeUI(this.getParent());
    }
    final JRootPane pane = getRootPane();
    for (AnActionButton button : myActions) {
      ShortcutSet shortcut = button.getShortcut();
      if (shortcut != null) {
        if (button instanceof MyActionButton && myCustomShortcuts != null ) {
          ShortcutSet customShortCut = myCustomShortcuts.get(((MyActionButton)button).myButton);
          if (customShortCut != null) {
            shortcut = customShortCut;
          }
        }
        if (button instanceof AddButton && UIUtil.isDialogRootPane(pane)) {
          button.registerCustomShortcutSet(shortcut, pane);
        } else {
          button.registerCustomShortcutSet(shortcut, button.getContextComponent());
        }
        if (button instanceof RemoveButton) {
          registerDeleteHook((MyActionButton)button);
        }
      }
    }

    super.addNotify(); // call after all to construct actions tooltips properly
  }

  private static void registerDeleteHook(final MyActionButton removeButton) {
    new AnAction(() -> IdeBundle.message("action.Anonymous.text.delete.hook")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeButton.actionPerformed(e);
      }

      @Override
      public boolean isDumbAware() {
        return removeButton.isDumbAware();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        final JComponent contextComponent = removeButton.getContextComponent();
        if (contextComponent instanceof JTable && ((JTable)contextComponent).isEditing()) {
          e.getPresentation().setEnabled(false);
          return;
        }
        final SpeedSearchSupply supply = SpeedSearchSupply.getSupply(contextComponent);
        if (supply != null && supply.isPopupActive()) {
          e.getPresentation().setEnabled(false);
          return;
        }
        removeButton.update(e);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), removeButton.getContextComponent());
  }

  public void setEnabled(Buttons button, boolean enabled) {
    final MyActionButton b = myButtons.get(button);
    if (b != null) {
      b.setEnabled(enabled);
    }
  }

  public void setCustomShortcuts(@NotNull Buttons button, ShortcutSet @Nullable ... shortcutSets) {
    if (shortcutSets != null) {
      if (myCustomShortcuts == null) myCustomShortcuts = new EnumMap<>(Buttons.class);
      myCustomShortcuts.put(button, new CompositeShortcutSet(shortcutSets));
    } else {
      if (myCustomShortcuts != null) {
        myCustomShortcuts.remove(button);
        if (myCustomShortcuts.isEmpty()) {
          myCustomShortcuts = null;
        }
      }
    }
  }

  @NotNull
  public ActionToolbarPosition getPosition() {
    return myPosition;
  }

  static abstract class MyActionButton extends AnActionButton implements DumbAware {
    private final Buttons myButton;
    protected final Listener myListener;

    MyActionButton(Buttons button, Listener listener, String name, Icon icon) {
      super(name, name, icon);
      myButton = button;
      myListener = listener;
    }

    @Override
    public ShortcutSet getShortcut() {
      return getCommonShortcut(myButton);
    }

    @Override
    public void updateButton(@NotNull AnActionEvent e) {
      super.updateButton(e);
      if (!e.getPresentation().isEnabled()) return;

      final JComponent c = getContextComponent();
      if (c instanceof JTable || c instanceof JList) {
        final ListSelectionModel model = c instanceof JTable ? ((JTable)c).getSelectionModel()
                                                             : ((JList)c).getSelectionModel();
        final int size = c instanceof JTable ? ((JTable)c).getRowCount()
                                             : ((JList)c).getModel().getSize();
        final int min = model.getMinSelectionIndex();
        final int max = model.getMaxSelectionIndex();
        e.getPresentation().setEnabled(isEnabled() && isEnabled(size, min, max));
      }
    }

    protected abstract boolean isEnabled(int size, int min, int max);

    //@Override
    //public boolean isEnabled() {
    //  if (myButton == Buttons.REMOVE) {
    //    final JComponent c = getContextComponent();
    //    if (c instanceof JTable && ((JTable)c).isEditing()) return false;
    //  }
    //  return super.isEnabled();
    //}
  }

  static class AddButton extends MyActionButton {
    AddButton(Listener listener, String name, Icon icon) {
      super(Buttons.ADD, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return true;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doAdd();
    }
  }

  static class RemoveButton extends MyActionButton {
    RemoveButton(Listener listener, String name, Icon icon) {
      super(Buttons.REMOVE, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doRemove();
    }
  }

  static class EditButton extends MyActionButton {
    EditButton(Listener listener, String name, Icon icon) {
      super(Buttons.EDIT, listener, name, icon);
    }

    @Override
    public void updateButton(@NotNull AnActionEvent e) {
      final JComponent c = getContextComponent();
      if (c == null || !c.isShowing() || !c.isEnabled()) {
        e.getPresentation().setEnabled(false);
        return;
      }

      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof KeyEvent &&
          c instanceof JTable &&
          ((JTable)c).isEditing() &&
          !(inputEvent.getComponent() instanceof ActionButtonComponent) // action button active in any case in the toolbar
      ) {
        e.getPresentation().setEnabled(false);
        return;
      }
      super.updateButton(e);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0 && min == max && min >= 0;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doEdit();
    }
  }

  static class UpButton extends MyActionButton {
    UpButton(Listener listener, String name, Icon icon) {
      super(Buttons.UP, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0 && min >= 1;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doUp();
    }
  }

  static class DownButton extends MyActionButton {
    DownButton(Listener listener, String name, Icon icon) {
      super(Buttons.DOWN, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0 && max < size-1;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doDown();
    }
  }

  @Contract("!null -> !null")
  public static ShortcutSet getCommonShortcut(Buttons button) {
    switch (button) {
      case ADD: return CommonShortcuts.getNewForDialogs();
      case EDIT: return CustomShortcutSet.fromString("ENTER");
      case REMOVE: return CustomShortcutSet.fromString(SystemInfo.isMac ? "meta BACK_SPACE" : "alt DELETE");
      case UP: return CommonShortcuts.MOVE_UP;
      case DOWN: return CommonShortcuts.MOVE_DOWN;
    }
    return null;
  }

  interface ListenerFactory {
    Listener createListener(CommonActionsPanel panel);
  }
}
