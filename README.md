> [!NOTE]
> Heyya! This branch (v0.8) is a large scale refactor and rewrite of the mod and will NOT be stable for some time. Some features have broken or flat out were removed for later re-implementation. A lot of undocumented and not fully complete new features are also added (cloud coloring (ability to specifiy color of a cloud), cloud tinting based off daytime, new lightning effects, a LOT of optomizations, a ton of new cloud types, storminess dry biome override, normal biome overriding, etc.) Everything below this note is the OG readme! ~ xvr6










![Title](https://i.imgur.com/naWBH5p.png)

# About

Simple Clouds is a cloud rendering overhaul mod for Minecraft: Java Edition, adding new cloud types, breathtaking visuals, and localized weather. It attempts to mimic real-life weather and cloud formations in a stylized, ambient, and aesthetic way that is meant to build on to the vanilla Minecraft experience.

**Simple Clouds is currently in open BETA, and you may experience bugs, crashes, and instability.** There are still lots of features I still want to implement.

# Info

Simple Clouds can work on the client-side only (connected to vanilla server or server without Simple Clouds installed), or with server-side support (singleplayer or on a server with Simple Clouds installed). There are limitiations/advantages in either case:

## Client-Side Only Limitations

- Localized weather is disabled and the vanilla, global weather system will be used.
- Cloud positions are not saved and the seed is randomized each time you login to a world. A custom cloud seed can be used that will keep the clouds the same each time you login if wanted.

## Server-Side Capabilities

- Localized weather + effects when under stormy clouds
- Cloud saving/synchronization with a unique seed per world

Simple Clouds also has a built-in cloud editor, which can be accessed in the main config menu. You can customize existing or create your own cloud types, and export them for use in datapacks/resourcepacks.

Cloud type JSONs are grouped by concern so custom definitions are easier to read and extend:

```json
{
  "visual": {
    "noise_settings": [
      {
        "fade_distance": 12.0,
        "height": 72.0,
        "height_offset": 8.0,
        "scale_x": 420.0,
        "scale_y": 90.0,
        "scale_z": 260.0,
        "value_offset": 1.1,
        "value_scale": 1.0
      }
    ],
    "transparency_fade": 0.06
  },
  "weather": {
    "type": "rain",
    "storminess": 0.34,
    "storm_start": 10.0,
    "storm_fade_distance": 64.0
  },
  "spawning": {
    "weight": 5
  }
}
```

`visual` controls how the cloud looks, `weather` controls precipitation and storm behavior, and `spawning` controls how cloud regions are generated. The loader still accepts the older flat format for compatibility, but newly exported cloud types use this grouped layout.

# How Does It Work?

Simple Clouds works by using compute shaders to generate the clouds in semi-realtime. It does this by iterating over a grid of voxels, testing each cube against layers of 3D noise, and adding vertices to create the clouds. This work is done on the GPU which handles parallel tasks such as these more efficiently than the CPU.

See the `cube_mesh.comp`, `SimpleCloudsRenderer`, and `CloudMeshGenerator` and its subclasses to see how it works.

Despite being super fast, Simple Clouds can still have a noticeable effect on your frames, especially if you have an older GPU. The client config has options for fine-tweaking the mod to see if you can get something that works well for your system. **In general, systems with more modern GPUs should handle Simple Clouds with ease.** I’m constantly looking for new ways to make this mod more performant, so it may get better over time.

# Contributions

If you have something that could help improve Simple Clouds (performance, features, etc.) feel free to make a pull request, or an issue in the issues tab. Please follow the [contributing guidelines](https://github.com/xvr6/simple-clouds/blob/1.20.1/docs/CONTRIBUTING.md) when contributing.

# License

Simple Clouds is licensed under [PolyForm Perimeter License 1.0.1](https://github.com/xvr6/simple-clouds/blob/1.21.1/LICENSE.md) by nonamecrackers2 unless otherwise stated. The following files contain code that are subject to different licenses:

- [/src/main/resources/assets/simpleclouds/shaders/program/storm_fog.fsh](https://github.com/xvr6/simple-clouds/blob/1.21.1/src/main/resources/assets/simpleclouds/shaders/program/storm_fog.fsh#L64C1-L89C3)
- [/src/main/resources/assets/simpleclouds/shaders/include/random.glsl](https://github.com/xvr6/simple-clouds/blob/1.21.1/src/main/resources/assets/simpleclouds/shaders/include/random.glsl)
- [/src/main/resources/assets/simpleclouds/shaders/include/random_hash.glsl](https://github.com/xvr6/simple-clouds/blob/1.21.1/src/main/resources/assets/simpleclouds/shaders/include/random_hash.glsl)
- [/src/main/resources/assets/simpleclouds/shaders/include/simplex_noise.glsl](https://github.com/xvr6/simple-clouds/blob/1.21.1/src/main/resources/assets/simpleclouds/shaders/include/simplex_noise.glsl)
- [/src/main/resources/assets/simpleclouds/shaders/compute/cloud_regions.comp](https://github.com/xvr6/simple-clouds/blob/1.21.1/src/main/resources/assets/simpleclouds/shaders/compute/cloud_regions.comp)
- [/src/main/resources/assets/simpleclouds/shaders/core/cloud_region_tex.fsh](https://github.com/xvr6/simple-clouds/blob/1.21.1/src/main/resources/assets/simpleclouds/shaders/core/cloud_region_tex.fsh)
- [/src/main/java/dev/nonamecrackers2/simpleclouds/client/event/SimpleCloudsClientEvents.java](https://github.com/xvr6/simple-clouds/blob/1.21.1/src/main/java/dev/nonamecrackers2/simpleclouds/client/event/SimpleCloudsClientEvents.java)
