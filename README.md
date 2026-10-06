# PolyNav

High-performance 3D Polygon Navigation Mesh (NavMesh) library and pathfinding engine for Minecraft 1.20.1 (Fabric).

Status: Work In Progress (WIP)

## Overview

PolyNav provides an asynchronous pathfinding architecture designed for custom entities and NPCs. It replaces vanilla node-based pathfinding with a polygonal navigation mesh capable of handling multi-level structures, terrain elevation, stairs, slabs, doors, and vertical drops.

Used as the core navigation library by [PrettySimpleNPCs](https://github.com/neuror1ston/PrettySimpleNPCs).

## Features

- Multi-threaded voxel world scanner and NavMesh baker.
- Asynchronous A* pathfinder offloading calculations from the main server thread.
- Funnel algorithm path smoothing with Bezier trajectory optimization.
- Jump link detection and door state traversal.
- Binary fast serialization and chunk-based cache for baked meshes.
- Client-side 3D OpenGL route visualizer for debugging.
- Public developer API (`NavMeshAPI`, `INavMeshAgent`) for custom entity integrations.

## Requirements

- Minecraft 1.20.1
- Fabric Loader >= 0.14.21
- Fabric API
- Java 17

## Building from Source

```bash
git clone https://github.com/neuror1ston/PolyNav.git
cd PolyNav
./gradlew build
```

Compiled jar will be located in `build/libs/`.

## Integration

Add PolyNav to your Fabric mod's `build.gradle`:

```groovy
dependencies {
    modImplementation files("polynav-0.1.0.jar")
}
```

And in `fabric.mod.json`:

```json
"depends": {
  "polynav": ">=0.1.0"
}
```

## License

This project is distributed under a Fair Source License.
- Non-commercial forks and usage on non-commercial servers are permitted.
- Content creators are explicitly allowed to record and stream gameplay/content created with this library, including monetized media on YouTube, Twitch, etc.
- Commercial usage (commercial/monetized servers, commercial forks, paid distribution) requires prior written consent from the author.

See `LICENSE` for the full license text.

## Contact

Email: anar1ston@proton.me
