# Whitehole Geo 🌟

![Editing Flip-Swap Galaxy](https://github.com/TheProtaganist/Whitehole-Geo/blob/main/images/ExampleImage.png)

**Whitehole Geo** is an enhanced level editor for *Super Mario Galaxy* and *Super Mario Galaxy 2* with revolutionary **AI-powered editing capabilities**.

> 🙏 **Built upon [Whitehole-Neo](https://github.com/SMGCommunity/Whitehole-Neo)** by the SMG Community - Enhanced with AI Commands and modern features.

## 🎉 Version 1.5 - Major Release!

**Whitehole Geo 1.5** brings groundbreaking AI-powered editing capabilities with working AI parsing and comprehensive object manipulation. This major release introduces multiple AI provider support and a completely rewritten command system.

## ✨ What's New in Version 1.5

### 🤖 AI Commands - Fully Working System!

**Revolutionary Natural Language Editing** - Now with multiple AI provider support:

- **Multiple AI Providers**: Choose from Gemini AI, Ollama (local), OpenAI, or built-in parsing
- **Working AI Parsing**: Complete rewrite with robust command interpretation
- **Smart Object Manipulation**: "add a Goomba at 100, 0, 200", "move all coins up by 5"
- **Intelligent Batch Operations**: Transform multiple objects simultaneously
- **Context-Aware Commands**: AI understands your galaxy structure and object relationships
- **Offline Support**: Works without API keys using advanced built-in command parsing
- **Error Recovery**: Smart error handling with suggestions for command corrections
- **Command History**: Track and repeat previous AI commands

**Supported AI Providers:**
- 🔥 **Gemini AI** - Google's powerful language model (API key required)
- 🏠 **Ollama** - Run AI models locally (no API key needed)
- 🤖 **OpenAI** - GPT models for advanced command understanding
- 🧠 **Built-in Parser** - Sophisticated offline command interpretation

### 🎮 Enhanced Controls

- **Improved Camera**: Shift + Left-click + drag for rotation (customizable)
- **Modern UI**: Clean interface with helpful tooltips and status feedback
- **Better Error Handling**: Clear messages and recovery options

## 🚀 Quick Start

1. **Install Java 11+** from [adoptium.net](https://adoptium.net/)
2. **Build from Source**: Run `build.bat` (Windows) or see **[📋 INSTALLATION.md](INSTALLATION.md)** for complete setup instructions
3. **Launch**: Use `Whitehole.bat` or `Whitehole-Advanced.bat`
4. **Start Editing**: Open a galaxy and try the AI Commands tab!

> 📋 **Need Help?** Check the **[INSTALLATION.md](INSTALLATION.md)** guide for detailed build instructions, project structure, troubleshooting, and AI configuration.

### 🤖 AI Commands Examples

**Basic Object Manipulation:**
```text
add a Goomba at 100, 0, 200
move all coins 5 units up
rotate the platform 45 degrees on Y axis
scale selected objects by 1.5x
delete all enemies in this zone
```

**Advanced Batch Operations:**
```text
create a ring of 8 coins around 0, 100, 0 with radius 200
move every Star Bit above y=500 down to y=400
rotate all platforms between x=100 and x=300 by 90 degrees
scale all small enemies by 2x but leave large ones unchanged
change all red coins to blue coins in the current layer
```

**Smart Context Commands:**
```text
arrange the selected objects in a line
duplicate this platform 5 times with 100 unit spacing
create a spiral staircase with these blocks
mirror all objects across the Z axis
group nearby coins into clusters
```

## 🎯 Core Features - Version 1.5

**Whitehole Geo 1.5** builds upon the solid foundation of the original Whitehole with revolutionary improvements:

- **🤖 AI-Powered Editing** - Multiple AI providers with working natural language interface
- **🧠 Advanced Object Manipulation** - Context-aware batch operations and smart transformations
- **⚡ Enhanced Stability** - Completely rewritten AI system with robust error handling
- **📊 Modern Object Database** - Support for the [new SMG Community database](https://github.com/SMGCommunity/galaxydatabase)
- **🎨 Improved UI** - Light/Dark modes with modern flat design and AI status indicators
- **↩️ Full Undo System** - Complete undo/redo for all editing operations including AI commands
- **📋 Copy & Paste** - Duplicate objects and arrangements with AI-assisted positioning
- **🌟 SMG1 Compatibility** - Full support for all Super Mario Galaxy 1 stages
- **📝 Enhanced Template System** - AI-generated templates and quick-start galaxy types
- **🔄 Command History** - Track and repeat AI commands with learning capabilities

## ⚙️ System Requirements

This is intended for **Java 11+**, though the program runs fine on newer Java versions as well. For newer Java versions, use the provided batch files or this command:

```bash
java --add-exports=java.desktop/sun.awt=ALL-UNNAMED --add-exports=java.desktop/sun.java2d=ALL-UNNAMED --add-exports=java.base/java.lang=ALL-UNNAMED --add-exports=java.base/java.nio=ALL-UNNAMED --add-exports=java.base/java.util=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED --add-exports=java.logging/sun.util.logging.resources=ALL-UNNAMED -jar Whitehole.jar
```

**UI Scaling**: Add `-Dsun.java2d.uiScale=1.5` to the command to scale the UI (adjust 1.5 as needed).

## 🎮 Controls

### Basic Controls

- **Left Click**: Select/Deselect object (hold Shift/Ctrl to select multiple)
- **Right Click**: Context menu for object
- **Shift + Left Click + Drag**: Rotate camera (default - NEW!)
- **Right Click + Drag**: Alternative camera rotation (optional)
- **Left Click + Drag**: Pan camera, Move object
- **Mouse Wheel**: Zoom in/out, Move object forward/backward
- **Middle Click**: Pan camera

### Editing Controls

- **Ctrl+C**: Copy selected objects
- **Ctrl+V**: Paste objects (positioned at mouse)
- **Ctrl+Z**: Undo action
- **Ctrl+Y**: Redo action
- **Delete**: Delete selected objects
- **Shift+A**: Add object quick access menu
- **H**: Hide/Unhide selected objects
- **Alt+H**: Unhide all hidden objects

### Camera Controls

- **F** / **Spacebar**: Focus camera on selected object
- **R**: Reset camera position
- **Arrow Keys + PageUp/PageDown**: Move camera (or WASD + EQ in settings)

### Object Manipulation

- **Hold P + Arrow Keys**: Move selected objects
- **Hold R + Arrow Keys**: Rotate selected objects
- **Hold S + Arrow Keys**: Scale selected objects

### View Controls

- **G**: Toggle grid display
- **B**: Toggle background rendering
- **L**: Toggle lighting
- **O**: Toggle object collision display

## 📚 Libraries

This project uses the following open-source libraries:

- **[jogamp](https://jogamp.org/)** - OpenGL bindings for Java
- **[gluegen](https://jogamp.org/gluegen/www/)** - Java/C binding generator
- **[org.json](https://github.com/stleary/JSON-java)** - JSON parsing library
- **[flatlaf](https://github.com/JFormDesigner/FlatLaf)** - Modern look and feel
- **[JWindowsFileDialog](https://github.com/JacksonBrienen/JWindowsFileDialog)** - Native file dialogs

## 🔧 Development

For detailed build instructions, project structure, troubleshooting, and development setup, see **[📋 INSTALLATION.md](INSTALLATION.md)**.

This comprehensive guide covers:

- Complete project structure and file organization
- Step-by-step build process (automated and manual)
- AI provider configuration (Gemini/Ollama)
- Troubleshooting common issues
- Development environment setup

## Credits

**Whitehole Geo** is built upon the excellent foundation of **[Whitehole-Neo](https://github.com/SMGCommunity/Whitehole-Neo)** by the SMG Community.

### Original Project
- **[Whitehole-Neo](https://github.com/SMGCommunity/Whitehole-Neo)** - The base Mario Galaxy level editor
- **SMG Community** - For maintaining and improving the original Whitehole project
- **Original Whitehole developers** - For creating the foundational editor

### This Enhanced Version
- **AI Commands System** - Revolutionary natural language editing interface
- **Enhanced Camera Controls** - Improved user experience with Shift+drag rotation
- **Comprehensive Documentation** - Complete installation and usage guides
- **Modern Build System** - Streamlined compilation and deployment

We extend our gratitude to all the original developers and the SMG Community for their incredible work that made this enhanced version possible.

## 📄 License

This project maintains the same license as the original Whitehole project.
