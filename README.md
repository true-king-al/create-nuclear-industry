# Create Nuclear Industrys

A Minecraft mod (NeoForge 1.21.1) adding nuclear reactor mechanics — fuel rods, radiation, heat management, and eventually full Create integration.

## Current Features

- **Uranium Fuel Rods** — emit radiation particles, heat up when irradiated, glow brighter as temperature rises, melt down to lava at 1000°C
- **Radiation Particles** — travel through the world, bounce off solid blocks, get absorbed by dense materials (iron reflects everything, obsidian absorbs heavily)
- **Copper Heat Pipes** — connect any two rods to share heat between them; right-click two rods to link, shift-click to disconnect

## Building

Requires Java 21. Clone the repo and run:

```
./gradlew build
```

To launch in a dev environment:

```
./gradlew runClient
```

## Contributors

- **Logan Larrabee** (true-king-al)
