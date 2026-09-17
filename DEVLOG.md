# Create Nuclear Industrys — Dev Tracking

---

## ✅ Completed

- **Uranium Ore** — generates in the world; also a 5% chance from crushing redstone. Mixing turns ore into enriched uranium nuggets, 9 nuggets craft enriched uranium
- **Uranium Fuel Rod** — placeable rod block that emits radiation particles, heats up when hit by them, glows and shifts color with temperature
- **Meltdowns** — at 1000°C a rod explodes (power 7–20, scaling with nearby rods), flings surrounding blocks and lava, and dumps heat into rods within 5 blocks so big reactors cascade
- **Boron Control Rod** — absorbs 60% of radiation particles that hit it
- **Heat Pipe** — block that equalizes heat with all 6 neighbors
- **Heat Gauge** — right-click readout, goggle tooltip, Create Display Link source, comparator output 0–15 over 0–1000°C
- **Radiation particles** — travel through the world, bounce off solid blocks, get absorbed by dense materials, heat up rods they hit
- **Radiation Sickness** — +1 s per particle absorbed; slowness, weakness, nausea, mining fatigue, occasional damage. Milk cures it (intended, #10)
- **Steam** — fluid, bucket, rising steam particles
- **Boiler** — takes water by pipe, turns it into steam when at 100°C+, pulls heat out while it has water
- **Steam Turbine** — steam in the back, spins at 16 RPM, uses 10% of the steam and passes 90% out the other faces. Capacity follows the steam flow averaged over the last 5 s: boiler heat × 10 SU (e.g. 287.6°C → ~2,876 SU), up to 10,000 SU at full flow (100 mB steam/tick)
- **Creative Heat Source** — holds a set temperature for testing
- **Recipes** — fuel rod, control rod, heat pipe, heat gauge, enriched uranium, boiler, steam turbine
- **Create-style shift tooltips** on the mod's blocks

### Fixed 2026-09-17
- **#1** Rods moved by pistons, Create contraptions, falling blocks or /setblock keep radiating from their new spot (heat carries over from the rod's glow level)
- **#2** Heat starts at and cools toward the biome's ambient temperature instead of 0°C — plains 20°C, desert ~57°C, snowy plains ~-5°C
- **#3** Creative and spectator players are immune to radiation; particles pass through them
- **#12** Display Link source name uses the correct lang key (`createnuclearindustrys.display_source.heat_gauge_temperature`)

---

## 🔧 Needs Rework / Technically Works But...

- **Block tags are broken** — `mineable/pickaxe` and `needs_iron_tool` still list the removed `thermal_generator`, so those tags fail to load on startup
- **Radiation is too strong (#4)** — brief exposure causes sickness, reactors run without a moderator, explosions are too easy to trigger
- **Radiation vs blocks (#5)** — iron reflects "neutrons" completely; plan is separate alpha/beta/gamma/neutron types (see NUCLEAR_RESEARCH.md)
- **Particle colors** — rod particles get a fully random color (#11)
- **Moved heat pipes / gauges / boilers** — only fuel rods were fixed for #1; the other heat blocks still drop out of the network when moved unless they land next to it
- **Heat dissipation** — flat rate toward ambient, no interaction with water/air/etc.
- **Config** — `Config.java` is still the NeoForge template example (dirt block logging, magic number)
- **Fuel rod color (#8)** — issue closed, but the promised config option to make rods grey doesn't exist yet

---

## 📋 Todo

- [ ] Publish a build with the #1/#2/#3/#12 fixes (promised on #3 and #12)
- [ ] Water cooling — water next to a rod pulls heat down
- [ ] Sound design — hum, crackle near meltdown
- [ ] Active cooling (#6) — coolant (water, molten salt) piped through the reactor, heat exchanger
- [ ] Multi-block turbine (#7) — plus a condenser / cooling tower, biome affects efficiency
- [ ] Cherenkov light in water (#9) — needs radiation types first
- [ ] Waterloggable fuel and control rods (#11)
- [ ] Hazmat suit (#11)
- [ ] Harder recipes, higher power output (#11)
- [ ] Lingering radiation after an explosion, ~30 min, configurable (#11)

---

## 💡 Ideas / Future

- **Radiation sickness tiers** — cumulative dose with escalating effects
- **Control rod regulation** — redstone-controlled insertion, SCRAM
- **Shielding blocks** — lead/concrete with better absorption than vanilla
- **Fuel depletion** — rods wear out and need replacing
- **Reactor multiblock** — structured build requirement for full efficiency
- **Geiger counter** — item that shows local radiation level
- **Cooling tower** — multiblock that actively dissipates heat
- **Molten core block** — proper meltdown hazard instead of plain lava
