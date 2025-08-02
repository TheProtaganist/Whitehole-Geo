# 📋 Whitehole Geo - Complete Installation Guide

This guide covers everything you need to build, install, and configure **Whitehole Geo** from source code.

## 📁 Project Structure

Understanding the project layout will help you navigate and troubleshoot issues:

```text
Whitehole-Geo/
├── 📄 build.xml                      # Main Ant build configuration
├── 🖼️ iconRAW.png                    # Application icon (source)
├── 📦 manifest.mf                    # JAR manifest template
├── 📋 README.md                      # Project overview and features
├── 📋 INSTALLATION.md                # This installation guide
├── 📝 sources.txt                    # Source file list for compilation
├── 🚀 build.bat                      # Windows build script
├── 🚀 Whitehole.bat                  # Standard launcher
├── 🚀 Whitehole-Advanced.bat         # Advanced launcher with debug options
│
├── 🔨 build/                         # Build output directory
│   ├── 📄 build.xml                  # NetBeans build configuration
│   ├── 📦 manifest.mf                # Build-specific manifest
│   └── 📁 classes/                   # Compiled .class files (after build)
│
├── 💾 data/                          # Application data and configuration
│   ├── 🌌 areamanagerlimits.json     # Area size limits
│   ├── 🌌 galaxies.json              # Galaxy definitions
│   ├── 🔍 hashlookup.txt             # Hash lookup table
│   ├── 💡 hints.json                 # Editor hints and tips
│   ├── 🔄 modelsubstitutions.json    # Model replacement rules
│   ├── 🗃️ objectdb.json              # Object database (SMG1/SMG2)
│   ├── ⌨️ shortcuts.json             # Keyboard shortcuts
│   ├── 🎨 specialrenderers.json      # Custom rendering rules
│   ├── 🌍 zones.json                 # Zone definitions
│   └── 📁 templates/                 # Galaxy templates
│       ├── 🌟 SMG1 1 Star Galaxy.json
│       ├── 🌌 SMG1 Big Galaxy.json
│       ├── 📦 SMG1BigGalaxy.arc
│       ├── 📦 SMG1BigGalaxyScenario.arc
│       ├── 📦 SMG1OneStarGalaxy.arc
│       ├── 📦 SMG1OneStarGalaxyScenario.arc
│       ├── 🌟 SMG2 Big Galaxy.json
│       ├── 🌟 SMG2 Small Galaxy.json
│       ├── 🌍 SMG2 Standard Zone.json
│       ├── 📦 SMG2BigGalaxyMap.arc
│       ├── 📦 SMG2BigGalaxyScenario.arc
│       ├── 📦 SMG2SmallGalaxyMap.arc
│       ├── 📦 SMG2SmallGalaxyScenario.arc
│       └── 📦 SMG2StandardZoneMap.arc
│
├── 🖼️ images/                        # Project images and screenshots
│   └── 📸 ExampleImage.png           # Example editor screenshot
│
├── 📚 lib/                           # External libraries (JAR files)
│   ├── 🔧 byte-buddy-1.14.10.jar     # Runtime code generation
│   ├── 🔧 byte-buddy-agent-1.14.10.jar # Bytecode manipulation agent
│   ├── 🎨 flatlaf-2.1.jar            # Modern look and feel
│   ├── 🎮 gluegen-rt.jar             # OpenGL binding runtime
│   ├── 🎮 jogamp-fat.jar             # OpenGL/Java bindings
│   ├── 📝 json-20201115.jar          # JSON parsing library
│   ├── 🧪 junit-jupiter-*.jar        # JUnit 5 testing framework (4 files)
│   ├── 🧪 junit-platform-*.jar       # JUnit platform (4 files)
│   ├── 📁 JWindowsFileDialog-0.81.jar # Native Windows file dialogs
│   ├── 🧪 mockito-core-5.8.0.jar     # Mocking framework for tests
│   └── 🔧 objenesis-3.3.jar          # Object instantiation library
│
├── 🏗️ nbproject/                     # NetBeans project configuration
│   ├── 🔨 build-impl.xml             # NetBeans build implementation
│   ├── ⚙️ genfiles.properties        # Generated files configuration
│   ├── 📄 licenseheader.txt          # License header template
│   ├── ⚙️ project.properties         # Project settings
│   └── 📄 project.xml                # NetBeans project definition
│
└── 💻 src/                           # Source code
    ├── 🎨 res/                       # Resources (icons)
    │   ├── 🖼️ icon32.png              # 32x32 icon
    │   ├── 🖼️ icon40.png              # 40x40 icon  
    │   ├── 🖼️ icon48.png              # 48x48 icon
    │   ├── 🖼️ icon56.png              # 56x56 icon
    │   └── 🖼️ icon64.png              # 64x64 icon
    │
    └── 🌌 whitehole/                 # Main application package
        ├── 📋 AboutForm.form/.java    # About dialog
        ├── 🏠 MainFrame.form/.java    # Main application window
        ├── ⚙️ Settings.java            # Application settings
        ├── ⚙️ SettingsForm.form/.java # Settings dialog
        ├── 🚀 Whitehole.java          # Main application entry point
        │
        ├── 🤖 ai/                    # AI Commands system (NEW!)
        │   ├── 🎛️ AICommandPanel.java        # AI interface panel
        │   ├── ↩️ AICommandUndoEntry.java     # Undo system integration
        │   ├── ❓ AIHelpSystem.java           # Contextual help
        │   ├── 🎯 AIModelSelector.java       # AI model selection
        │   ├── 🔌 AIProvider.java            # AI provider interface
        │   ├── ⚠️ AIProviderException.java    # AI error handling
        │   ├── 🔧 AIProviderManager.java     # Provider management
        │   ├── 💬 AIResponse.java            # AI response handling
        │   ├── ⚡ CommandExecutor.java       # Command execution engine
        │   ├── 📖 CommandExecutorExample.java # Usage examples
        │   ├── 🔗 CommandExecutorIntegration.java # Editor integration
        │   ├── 📝 CommandParser.java         # Natural language parsing
        │   ├── ✅ CommandResult.java         # Execution results
        │   ├── 🧪 ComprehensiveValidationTest.java # Test suite
        │   ├── ❓ DisambiguationDialog.java  # Clarification dialogs
        │   ├── 🚨 ErrorHandler.java          # Error management
        │   ├── 🌌 GalaxyContext.java         # Galaxy state context
        │   ├── 💾 GalaxyContextCache.java    # Context caching
        │   ├── 🔧 GalaxyContextManager.java  # Context management
        │   └── ⚡ LazyObjectInfo.java        # Lazy object loading
        │
        ├── 🗃️ db/                    # Database and object management
        ├── ✏️ editor/                # Galaxy editor components  
        ├── 💾 io/                    # File I/O operations
        ├── 📐 math/                  # Mathematical utilities
        ├── 🎨 rendering/             # 3D rendering system
        ├── 🌌 smg/                   # Super Mario Galaxy specific code
        ├── 🎨 theme/                 # UI theming
        └── 🔧 util/                  # General utilities
```

## 🎯 Key Files Explained

### 🚀 Launch Scripts

- **`build.bat`** - Compiles the entire project from source
- **`Whitehole.bat`** - Standard launcher with proper JVM settings  
- **`Whitehole-Advanced.bat`** - Debug launcher with extended logging

### 🤖 AI System (Major New Feature)

The `src/whitehole/ai/` directory contains the revolutionary AI Commands system:

- **Natural language processing** for editor commands
- **Multi-provider support** (Gemini AI, Ollama, built-in parsing)
- **Galaxy context awareness** for intelligent suggestions
- **Comprehensive error handling** and user guidance

### 📦 Dependencies (`lib/` directory)

All required JAR files are included:

- **jogamp/gluegen** - OpenGL bindings for 3D rendering
- **flatlaf** - Modern UI look and feel
- **org.json** - JSON parsing for configuration files
- **JWindowsFileDialog** - Native file dialogs on Windows

## 🚀 Quick Start

### Prerequisites

- **Java Development Kit (JDK) 11 or newer**
  - Download from [Eclipse Temurin](https://adoptium.net/) (recommended)
  - Or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- **Super Mario Galaxy ROM files** (not included - you must provide your own)

### Installation Steps

1. **Clone or Download the Project**

   ```bash
   git clone <repository-url>
   # OR download and extract the ZIP file
   ```

2. **Navigate to Project Directory**

   ```bash
   cd Whitehole-Geo
   ```

3. **Build from Source**
   
   **Windows:**

   ```cmd
   build.bat
   ```
   
   **Manual Build (All Platforms):**

   ```bash
   # Compile the source code
   javac -cp "lib/*" -d build/classes -sourcepath src src/whitehole/Whitehole.java
   
   # Copy resources
   xcopy /e /i /y src\res build\classes\res   # Windows
   cp -r src/res build/classes/res             # Linux/Mac
   
   # Create JAR file
   jar cfm Whitehole.jar manifest.mf -C build/classes .
   ```

4. **Run Whitehole**
   
   **Windows:**

   ```cmd
   Whitehole.bat
   ```
   
   **Manual Launch:**

   ```bash
   java --add-exports=java.desktop/sun.awt=ALL-UNNAMED --add-exports=java.desktop/sun.java2d=ALL-UNNAMED --add-exports=java.base/java.lang=ALL-UNNAMED --add-exports=java.base/java.nio=ALL-UNNAMED --add-exports=java.base/java.util=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED --add-exports=java.logging/sun.util.logging.resources=ALL-UNNAMED -jar Whitehole.jar
   ```

## 🔧 Build Scripts

### Windows Build Script (`build.bat`)

Automatically compiles and packages Whitehole. Run this whenever you make changes to the source code.

### Manual Build Commands

**Compile:**

```bash
javac -cp "lib/*" -d build/classes -sourcepath src src/whitehole/Whitehole.java
```

**Package:**

```bash
jar cfm Whitehole.jar manifest.mf -C build/classes .
```

## 🎮 Features

### Core Features

- **Galaxy Editor**: Edit Super Mario Galaxy levels with a visual 3D interface
- **Object Manipulation**: Add, move, rotate, and scale game objects
- **Multiple Game Support**: SMG1 and SMG2 compatibility
- **Advanced Rendering**: 3D preview with proper lighting and textures

### AI Commands (New!)

- **Natural Language Processing**: Control the editor with plain English
- **Smart Object Placement**: "add a Goomba at 100, 0, 200"
- **Batch Operations**: "move all coins 5 units up"
- **Intelligent Transformations**: "rotate the platform 45 degrees"

## 🤖 AI Configuration

### Built-in Parser (Default)

The AI system works out-of-the-box with built-in natural language parsing. No API keys required!

### Optional: Gemini AI Integration

For enhanced AI capabilities, you can configure Google Gemini:

1. **Get a Gemini API Key** from [Google AI Studio](https://aistudio.google.com/)
2. **Create configuration file** `ai_config.json` in the project root:

   ```json
   {
     "provider": "gemini",
     "gemini_api_key": "your-api-key-here",
     "model": "gemini-pro"
   }
   ```

3. **Restart Whitehole** - The AI system will automatically detect and use Gemini

### Optional: Ollama Integration

For local AI processing with Ollama:

1. **Install Ollama** from [ollama.ai](https://ollama.ai/)
2. **Pull a model** (e.g., `ollama pull llama2`)
3. **Create configuration file** `ai_config.json`:

   ```json
   {
     "provider": "ollama",
     "ollama_url": "http://localhost:11434",
     "model": "llama2"
   }
   ```

4. **Restart Whitehole** - Ollama integration will be active

## 💡 AI Commands Usage

### Basic Commands

```text
add a Goomba at 100, 0, 200
create a coin at the origin
place a platform at 50, 100, 0
```

### Batch Operations

```text
move all coins 5 units up
rotate all platforms 45 degrees
scale selected objects by 1.5x
delete all Goombas in this area
```

### Smart Transformations

```text
create a ring of coins around 0, 100, 0
make a staircase of platforms going up
arrange the stars in a circle
```

## 🐛 Troubleshooting

### Common Issues

**"Class not found" errors:**
- Ensure all JAR files are in the `lib/` directory
- Check that the classpath includes `lib/*`

**"Module access" errors on Java 11+:**
- Use the provided batch files which include necessary `--add-exports` flags
- Or add the flags manually as shown in the manual launch command

**AI Commands not responding:**
- Check if `ai_config.json` is valid JSON
- Verify API keys and network connectivity
- The built-in parser should work without any configuration

**Graphics issues:**
- Update your graphics drivers
- Try running with `-Dsun.java2d.opengl=false` to disable OpenGL acceleration

### Performance Optimization

**Large Galaxy Loading:**
- Increase heap size: `-Xmx4G` (adjust as needed)
- Enable parallel GC: `-XX:+UseParallelGC`

**UI Scaling:**
- Scale UI for high-DPI displays: `-Dsun.java2d.uiScale=1.5`

## 🧪 Development

### Setting up Development Environment

1. **IDE Setup**: Import as a standard Java project in your IDE
2. **Dependencies**: All required JARs are in `lib/`
3. **Build Configuration**: Use the provided Ant build files or manual commands
4. **Testing**: Run individual components or use the full application

### Contributing

1. **Code Style**: Follow existing Java conventions
2. **AI System**: AI commands are in `src/whitehole/ai/`
3. **Testing**: Test with both SMG1 and SMG2 files
4. **Documentation**: Update this guide when adding new features

### Advanced Configuration

**Custom JVM Options:**

```bash
# High memory for large galaxies
-Xmx8G -Xms2G

# Performance tuning
-XX:+UseG1GC -XX:MaxGCPauseMillis=200

# Debug mode
-Xdebug -Xruntimedebug
```

## 📋 System Requirements

### Minimum Requirements

- **Java**: JDK 11 or newer
- **RAM**: 2GB available
- **Graphics**: OpenGL 2.0 compatible
- **Storage**: 500MB for application + space for ROM files

### Recommended Requirements

- **Java**: JDK 17 or newer
- **RAM**: 4GB+ available
- **Graphics**: Dedicated GPU with OpenGL 3.0+
- **Storage**: 2GB+ available space

## 📄 License

This project maintains the same license as the original Whitehole project.

---

**Need more help?** Check the [README.md](README.md) for feature overview and usage examples.
