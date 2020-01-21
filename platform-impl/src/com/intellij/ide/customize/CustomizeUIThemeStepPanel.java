// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.CommonBundle;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class CustomizeUIThemeStepPanel extends AbstractCustomizeWizardStep {
  public static class ThemeInfo {
    public final String name;
    public final String previewFileName;
    public final String laf;

    private Icon icon;

    public ThemeInfo(String name, String previewFileName, String laf) {
      this.name = name;
      this.previewFileName = SystemInfo.isMac && "IntelliJ".equals(previewFileName) ? "Aqua" : previewFileName;
      this.laf = laf;
    }

    private Icon getIcon() {
      if (icon == null) {
        String selector;
        if (SystemInfo.isMac) {
          selector = "OSX";
        }
        else if (SystemInfo.isWindows) {
          selector = "Windows";
        }
        else {
          selector = "Linux";
        }
        icon = IconLoader.getIcon("/lafs/" + selector + previewFileName + ".png");
      }
      return icon;
    }

    public void apply() {
    }
  }

  protected static final ThemeInfo DARCULA = new ThemeInfo("Darcula", "Darcula", DarculaLaf.class.getName());
  protected static final ThemeInfo INTELLIJ = new ThemeInfo("Light", "IntelliJ", IntelliJLaf.class.getName());

  private final boolean myColumnMode;
  private final JLabel myPreviewLabel;
  private final Set<ThemeInfo> myThemes = new LinkedHashSet<>();

  public CustomizeUIThemeStepPanel() {
    setLayout(createSmallBorderLayout());

    initThemes(myThemes);

    myColumnMode = myThemes.size() > 2;
    JPanel buttonsPanel = new JPanel(new GridLayout(myColumnMode ? myThemes.size() : 1, myColumnMode ? 1 : myThemes.size(), 5, 5));
    ButtonGroup group = new ButtonGroup();
    final ThemeInfo myDefaultTheme = getDefaultTheme();

    for (final ThemeInfo theme : myThemes) {
      final JRadioButton radioButton = new JRadioButton(theme.name, myDefaultTheme == theme);
      radioButton.setOpaque(false);
      final JPanel panel = createBigButtonPanel(createSmallBorderLayout(), radioButton, () -> {
        CustomizeIDEWizardInteractions.INSTANCE.record(CustomizeIDEWizardInteractionType.UIThemeChanged);

        applyLaf(theme, this);
        theme.apply();
      });
      panel.setBorder(createSmallEmptyBorder());
      panel.add(radioButton, myColumnMode ? BorderLayout.WEST : BorderLayout.NORTH);
      Icon icon = theme.getIcon();
      int maxThumbnailSize = 400 / myThemes.size();
      final JLabel label = new JLabel(
        myColumnMode ? IconUtil.scale(IconUtil.cropIcon(icon, maxThumbnailSize * 2, maxThumbnailSize * 2), this, .5f) : icon);
      label.setVerticalAlignment(SwingConstants.TOP);
      label.setHorizontalAlignment(SwingConstants.RIGHT);
      panel.add(label, BorderLayout.CENTER);

      group.add(radioButton);
      buttonsPanel.add(panel);
    }
    add(buttonsPanel, BorderLayout.CENTER);
    myPreviewLabel = new JLabel();
    myPreviewLabel.setHorizontalAlignment(myColumnMode ? SwingConstants.LEFT : SwingConstants.CENTER);
    myPreviewLabel.setVerticalAlignment(SwingConstants.CENTER);
    if (myColumnMode) {
      add(buttonsPanel, BorderLayout.WEST);
      JPanel wrapperPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      wrapperPanel.add(myPreviewLabel);
      add(wrapperPanel, BorderLayout.CENTER);
    }
    //Static fields initialization. At this point there is no parent window
    applyLaf(myDefaultTheme, this);
    //Actual UI initialization
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> applyLaf(myDefaultTheme, this));
  }

  protected void initThemes(Collection<? super ThemeInfo> result) {
    if (SystemInfo.isMac) {
      result.add(DARCULA);
      result.add(getDefaultLafOnMac());
    }
    else if (SystemInfo.isWindows) {
      result.add(DARCULA);
      result.add(INTELLIJ);
    }
    else {
      result.add(DARCULA);
      result.add(INTELLIJ);
    }
  }

  @NotNull
  protected static ThemeInfo getDefaultLafOnMac() {
    return INTELLIJ;
  }

  @NotNull
  private ThemeInfo getDefaultTheme() {
    if (ApplicationManager.getApplication() != null) {
      if (StartupUiUtil.isUnderDarcula()) return DARCULA;
      return INTELLIJ;
    }
    CloudConfigProvider provider = CloudConfigProvider.getProvider();
    if (provider != null) {
      String lafClassName = provider.getLafClassName();
      if (lafClassName != null) {
        ThemeInfo result = ContainerUtil.find(myThemes, theme -> lafClassName.equals(theme.laf));
        if (result != null) {
          return result;
        }
      }
    }
    return myThemes.iterator().next();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.width += 30;
    return size;
  }

  @Override
  public String getTitle() {
    return "UI Themes";
  }

  @Override
  public String getHTMLHeader() {
    return "<html><body><h2>Set UI theme</h2>&nbsp;</body></html>";
  }

  @Override
  public String getHTMLFooter() {
    return "You can change the UI theme later in " +
           CommonBundle.settingsTitle()
           + " | " + OptionsBundle.message("configurable.group.appearance.settings.display.name")
           + " | " + "Appearance. Additional themes are available in " + CommonBundle.settingsTitle() + " | Plugins.";
  }

  private void applyLaf(ThemeInfo theme, Component component) {
    UIManager.LookAndFeelInfo info = new UIManager.LookAndFeelInfo(theme.name, theme.laf);
    try {
      boolean wasUnderDarcula = StartupUiUtil.isUnderDarcula();
      UIManager.setLookAndFeel(info.getClassName());
      AppUIUtil.updateForDarcula(StartupUiUtil.isUnderDarcula());
      String className = info.getClassName();
      WelcomeWizardUtil.setWizardLAF(className);
      Window window = SwingUtilities.getWindowAncestor(component);
      if (window != null) {
        if (SystemInfo.isMac) {
          window.setBackground(new Color(UIUtil.getPanelBackground().getRGB()));
        }
        SwingUtilities.updateComponentTreeUI(window);
      }
      if (ApplicationManager.getApplication() != null) {
        LafManager lafManager = LafManager.getInstance();
        lafManager.setCurrentLookAndFeel(info);
        if (lafManager instanceof LafManagerImpl) {
          ((LafManagerImpl)lafManager).updateWizardLAF(wasUnderDarcula);//Actually updateUI would be called inside EditorColorsManager
        }
        else {
          lafManager.updateUI();
        }
      }
      if (myColumnMode) {
        myPreviewLabel.setIcon(theme.getIcon());
        myPreviewLabel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Label.foreground")));
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
