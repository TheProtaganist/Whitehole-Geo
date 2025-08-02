/*
 * Copyright (C) 2022 Whitehole Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package whitehole.theme;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import javax.swing.UIDefaults;

/**
 * Custom cyan-themed Look and Feel for Whitehole Geo
 */
public class FlatCyanLaf extends FlatLightLaf {
    
    public static final String NAME = "FlatLaf Cyan";
    
    public static boolean setup() {
        return setup(new FlatCyanLaf());
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getDescription() {
        return "FlatLaf Cyan Look and Feel";
    }
    
    @Override
    protected void initClassDefaults(UIDefaults defaults) {
        super.initClassDefaults(defaults);
    }
    
    @Override
    protected void initSystemColorDefaults(UIDefaults defaults) {
        super.initSystemColorDefaults(defaults);
    }
    
    @Override
    protected void initComponentDefaults(UIDefaults defaults) {
        super.initComponentDefaults(defaults);
        
        // Define professional light blue color palette
        Color cyanPrimary = new Color(0, 150, 255);      // Professional blue
        Color cyanLight = new Color(100, 180, 255);      // Light blue
        Color cyanDark = new Color(0, 100, 200);         // Dark blue
        Color cyanAccent = new Color(0, 200, 255);       // Bright cyan accent
        Color cyanBackground = new Color(240, 248, 255); // AliceBlue background
        Color cyanSurface = new Color(248, 250, 252);    // Light gray-blue surface
        Color cyanBorder = new Color(200, 220, 240);     // Light blue border
        
        // Apply cyan theme colors
        Object[] cyanColors = {
            // Accent and selection colors
            "Component.accentColor", cyanPrimary,
            "Component.focusColor", cyanLight,
            "Component.linkColor", cyanAccent,
            
            // Button colors
            "Button.default.background", cyanPrimary,
            "Button.default.foreground", Color.WHITE,
            "Button.default.focusedBackground", cyanLight,
            "Button.default.hoverBackground", cyanLight,
            "Button.default.pressedBackground", cyanDark,
            "Button.background", Color.WHITE,
            "Button.foreground", new Color(50, 50, 50),
            "Button.hoverBackground", cyanLight,
            "Button.pressedBackground", cyanDark,
            "Button.selectedBackground", cyanPrimary,
            "Button.disabledSelectedBackground", cyanDark.darker(),
            
            // Menu colors
            "Menu.selectionBackground", cyanPrimary,
            "MenuItem.selectionBackground", cyanPrimary,
            "MenuBar.selectionBackground", cyanPrimary,
            
            // Tab colors
            "TabbedPane.selectedBackground", Color.WHITE,
            "TabbedPane.hoverColor", cyanLight,
            "TabbedPane.focusColor", cyanLight,
            "TabbedPane.underlineColor", cyanPrimary,
            "TabbedPane.selectedForeground", cyanPrimary,
            
            // Table colors
            "Table.background", Color.WHITE,
            "Table.foreground", new Color(50, 50, 50),
            "Table.selectionBackground", cyanPrimary,
            "Table.selectionForeground", Color.WHITE,
            "Table.focusCellBackground", cyanLight.darker(),
            
            // Tree colors
            "Tree.background", Color.WHITE,
            "Tree.foreground", new Color(50, 50, 50),
            "Tree.selectionBackground", cyanPrimary,
            "Tree.selectionForeground", Color.WHITE,
            
            // List colors
            "List.background", Color.WHITE,
            "List.foreground", new Color(50, 50, 50),
            "List.selectionBackground", cyanPrimary,
            "List.selectionForeground", Color.WHITE,
            
            // Text field colors
            "TextField.background", Color.WHITE,
            "TextField.foreground", new Color(50, 50, 50),
            "TextField.focusedBorderColor", cyanPrimary,
            "TextField.selectionBackground", cyanPrimary,
            "TextArea.background", Color.WHITE,
            "TextArea.foreground", new Color(50, 50, 50),
            "TextArea.focusedBorderColor", cyanPrimary,
            "TextArea.selectionBackground", cyanPrimary,
            "TextPane.background", Color.WHITE,
            "TextPane.foreground", new Color(50, 50, 50),
            "TextPane.focusedBorderColor", cyanPrimary,
            "TextPane.selectionBackground", cyanPrimary,
            
            // Label colors
            "Label.background", Color.WHITE,
            "Label.foreground", new Color(50, 50, 50),
            
            // ComboBox colors
            "ComboBox.background", Color.WHITE,
            "ComboBox.foreground", new Color(50, 50, 50),
            "ComboBox.buttonBackground", Color.WHITE,
            "ComboBox.buttonArrowColor", cyanPrimary,
            "ComboBox.selectionBackground", cyanPrimary,
            
            // Checkbox and RadioButton colors
            "CheckBox.icon.focusedBorderColor", cyanPrimary,
            "CheckBox.icon.selectedBorderColor", cyanPrimary,
            "CheckBox.icon.selectedBackground", cyanPrimary,
            "CheckBox.icon.checkmarkColor", Color.WHITE,
            "RadioButton.icon.focusedBorderColor", cyanPrimary,
            "RadioButton.icon.selectedBorderColor", cyanPrimary,
            "RadioButton.icon.selectedBackground", cyanPrimary,
            
            // Slider colors
            "Slider.thumbColor", cyanPrimary,
            "Slider.trackColor", cyanDark,
            "Slider.focusedColor", cyanLight,
            
            // ProgressBar colors
            "ProgressBar.foreground", cyanPrimary,
            "ProgressBar.selectionForeground", Color.WHITE,
            "ProgressBar.selectionBackground", cyanDark,
            
            // ScrollBar colors
            "ScrollBar.thumb", cyanPrimary.darker(),
            "ScrollBar.hoverThumbColor", cyanPrimary,
            "ScrollBar.pressedThumbColor", cyanDark,
            
            // Separator colors
            "Separator.foreground", cyanBorder,
            
            // Border colors
            "Component.focusedBorderColor", cyanPrimary,
            "Component.borderColor", cyanBorder,
            
            // Panel and background colors
            "Panel.background", Color.WHITE,
            "Panel.foreground", new Color(50, 50, 50),
            "TabbedPane.background", Color.WHITE,
            "TabbedPane.foreground", new Color(50, 50, 50),
            "MenuBar.background", Color.WHITE,
            "MenuBar.foreground", new Color(50, 50, 50),
            "Menu.background", Color.WHITE,
            "Menu.foreground", new Color(50, 50, 50),
            "PopupMenu.background", Color.WHITE,
            "PopupMenu.foreground", new Color(50, 50, 50),
            
            // Tooltip colors
            "ToolTip.background", Color.WHITE,
            "ToolTip.foreground", new Color(50, 50, 50),
            
            // Title colors
            "TitledBorder.titleColor", cyanPrimary,
            
            // Window colors
            "window", Color.WHITE,
            "control", Color.WHITE,
            "controlHighlight", cyanLight,
            "controlShadow", cyanDark,
            
            // Additional accent colors for various components
            "Component.accentBaseColor", cyanPrimary,
            "Objects.Blue", cyanPrimary,
            "Objects.Green", new Color(0, 200, 150), // Teal-green
            "Objects.Red", new Color(255, 100, 100),  // Keep red for errors
            "Objects.Yellow", new Color(255, 193, 7), // Keep yellow for warnings
            "Objects.Orange", new Color(255, 152, 0), // Keep orange
            "Objects.Purple", new Color(156, 39, 176) // Keep purple
        };
        
        defaults.putDefaults(cyanColors);
    }
}