# TabGen - Minecraft Tab List Screenshot Generator

A Minecraft Forge mod that generates high-quality screenshots of the player tab list with customizable formatting, team colors, and player heads.

## Features

- Capture organized screenshots of the Minecraft tab list
- Automatic player sorting by teams
- Real-time player head fetching from Minotar API
- Colored player names with team prefixes and suffixes
- Ping indicators with signal strength visualization
- Clean, professional output with configurable styling
- Timestamp-based file naming for easy organization

## Installation

1. Ensure you have Minecraft Forge installed
2. Download the latest version of TabGen from releases
3. Place the .jar file in your Minecraft mods folder
4. Launch Minecraft with the Forge profile

## Usage

1. Join any Minecraft server
2. Open the chat and type `/tabgen`
3. The screenshot will be automatically saved in your Minecraft directory
4. Output files are named `tablist_YYYY-MM-DD_HH.mm.ss.png`

## Technical Details

### Requirements
- Minecraft Forge
- Java 8 or higher
- Minimum 512MB RAM allocation

### Performance Features
- Multi-threaded player head loading
- Caching system for player heads and fonts
- Optimized thread pool configuration
- Concurrent image processing
- Memory-efficient buffered operations

### Configuration
- Block Width: 278 pixels
- Block Height: 20 pixels
- Spacing: 22 pixels
- Head Size: 16x16 pixels
- Maximum Cache Size: 1000 entries
- Cache Expiry: 30 minutes

## Developer Notes

### Key Components
- Threaded execution for non-blocking operation
- Google Guava cache implementation
- Efficient image processing with BufferedImage
- Custom color mapping for Minecraft color codes
- Automated resource management

### Color Codes
Supports all standard Minecraft color codes (§0-§f):
- Black (§0)
- Dark Blue (§1)
- Dark Green (§2)
- Dark Aqua (§3)
- Dark Red (§4)
- Dark Purple (§5)
- Gold (§6)
- Gray (§7)
- Dark Gray (§8)
- Blue (§9)
- Green (§a)
- Aqua (§b)
- Red (§c)
- Light Purple (§d)
- Yellow (§e)
- White (§f)

## Version History

Current Version: 1.1
- Improved thread pool management
- Added head caching system
- Enhanced error handling
- Optimized memory usage
- Added professional watermark

## Credits

Created by Turki
Using Minotar API for player heads

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues and feature requests, please use the GitHub issues tracker.

---

*Made with TabGen v1.1*
