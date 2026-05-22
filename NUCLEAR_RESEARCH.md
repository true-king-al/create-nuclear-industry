# Nuclear Research — Create Nuclear Industrys

Quick reference for real-world nuclear concepts and how they could map to mod mechanics.

---

## Uranium Fuel Rods

Real rods are ceramic UO2 pellets inside zirconium alloy (zircaloy) tubes, enriched to 3–5% U-235.
Each fission releases ~200 MeV, mostly as heat from fragment kinetic energy.

**Burnup** (measured in MWd/tU) tracks how much energy has been extracted:
- Fresh rod: mostly U-235 fissioning
- Mid-life (~60% burnup): Pu-239 (bred from U-238) contributes up to 1/3 of fissions — peak efficiency
- End of life: both U-235 and Pu-239 depleted, efficiency drops
- **Xenon-135** (a fission product) builds up and "poisons" the reaction, especially right after shutdown — makes restarting hard and was a direct cause of the Chernobyl disaster

**Spent rods** still emit significant decay heat after removal:
- ~7% of full operating power at moment of shutdown
- Tapers off over hours/days but never zero immediately
- Must sit in cooling pools for years

**Mod potential:**
- Burnup stat (0–100%) tracked in SavedData per rod
- Peak output at ~60%, then declining
- Xenon poisoning mechanic: efficiency penalty after shutdown, clears over time
- Spent rods continue radiating + generating heat after removal

---

## Radiation Types

Four distinct types with very different behavior:

| Type    | Range in air | Stopped by                        | Special                              |
|---------|-------------|-----------------------------------|--------------------------------------|
| Alpha   | ~5 cm       | Paper, skin, touching the block   | Devastating if inhaled/ingested      |
| Beta    | 1–3 m       | Few mm aluminum, thin walls       | Skin burns at high intensity         |
| Gamma   | 10–30 m     | Lead (cm) or thick concrete/stone | Primary external hazard              |
| Neutron | Very deep   | Water, polyethylene, concrete     | Activates nearby materials           |

**Neutron activation:** stable metal blocks near a reactor absorb neutrons and become radioactive themselves — a major real-world problem (reactor vessels embrittle and become radioactive over decades).

**Mod potential:**
- Add `type` field to `RadiationParticle` (ALPHA, BETA, GAMMA, NEUTRON)
- Gamma particles phase through most blocks
- Neutron particles pass through nearly everything but are absorbed by water; activate nearby metal blocks into secondary emitters
- Alpha: contact-only damage, no external range but devastating if player is "contaminated"
- Different rendering colors per type (already have RGB on particles)

---

## Heat Generation

Almost all reactor heat comes from fission fragments slowing down inside the fuel pellet itself.
Fuel centerline temperature: 700–1,600°C even when coolant is only 300°C (ceramic fuel has terrible thermal conductivity).

**Key limits:**
- Linear heat generation rate > ~45 kW/m → fuel centerline melts
- Zircaloy cladding fails above ~1,200°C in steam
- Zirconium + steam → zirconium oxide + **hydrogen gas** → explosive (TMI, Fukushima)

**Decay heat curve after shutdown:**  
`Power = 0.066 × P₀ × t^-0.2` (t in seconds)  
A 3,000 MWt reactor still produces ~100 MW of heat one hour after shutdown.

**Mod potential:**
- Temperature stat on reactor structure
- Requires active coolant flow (fluid pipes) to stay safe
- Cladding failure triggers fission product release into coolant
- Decay heat: reactor stays hot for some time after being "turned off" — can't just abandon it
- Steam output → Create rotational power via turbine

---

## Chain Reactions & Criticality

**k-factor:** effective neutron multiplication
- k < 1: subcritical, reaction dies
- k = 1: critical, steady power (normal operation)
- k > 1: supercritical, power rises
- **Prompt critical:** chain reaction sustained by prompt neutrons alone (no delayed neutrons needed) → power excursion in microseconds → explosion. This is what happened at Chernobyl.

**Why reactors are controllable:** ~0.65% of neutrons are "delayed" (from fission product decay, half-lives 0.2–55 sec). This slows the effective response time to seconds, making mechanical control rods feasible.

**Moderator:** slows fast neutrons (2 MeV) to thermal energies (0.025 eV) so U-235 can absorb them efficiently.
- Light water (H₂O): most common, absorbs some neutrons, requires enriched fuel
- Heavy water (D₂O): barely absorbs neutrons, can use natural uranium (CANDU)
- Graphite: used in Chernobyl's RBMK; has a **positive void coefficient** — losing coolant increases reactivity, inherently unstable

**Control rods:** boron carbide (B₄C), hafnium, or silver-indium-cadmium. Absorb neutrons.
**SCRAM:** emergency full insertion of all control rods.

**Mod potential:**
- Multiblock reactor: fuel rods + moderator blocks + control rods + coolant
- Control rod GUI or redstone interface for power regulation
- SCRAM lever
- Xenon poisoning after shutdown makes restarting take time
- Going prompt-critical triggers a power excursion / explosion

---

## Cooling Systems

**PWR (Pressurized Water Reactor) — most common design:**
- Primary loop: pressurized water (~155 bar, 325°C) flows through core, picks up heat, goes to steam generator
- Secondary loop: steam drives turbines, condenses, returns (not radioactive under normal operation)
- Tertiary: cooling towers reject waste heat to environment

**Loss-of-coolant accident (LOCA):**
- Coolant loss → decay heat not removed → temperature rises → zircaloy oxidizes → hydrogen → explosion → core melt
- Corium (molten fuel + steel) can melt through reactor vessel floor ("China Syndrome")

**Reactor types for comparison:**

| Type | Coolant | Moderator | Temp | Efficiency | Notes |
|------|---------|-----------|------|------------|-------|
| PWR | Light water | Light water | 325°C | ~33% | Most common |
| BWR | Light water (boiling) | Light water | 286°C | ~33% | Direct steam to turbine (Fukushima) |
| CANDU | Heavy water | Heavy water | 310°C | ~30% | Natural uranium fuel |
| RBMK | Light water | Graphite | ~280°C | ~17% | Positive void coeff. (Chernobyl) |
| Sodium fast | Liquid sodium | None | 550°C | ~40% | Breeds Pu; sodium + water = violent reaction |
| HTGR | Helium | Graphite | 900°C | ~45% | TRISO fuel, very safe |

**Mod potential:**
- Fluid pipe network for coolant
- Different coolant fluids: water, heavy water, liquid sodium (higher efficiency, reacts with water violently)
- Passive vs active cooling (gravity-fed emergency tank)
- Sodium fast reactor as endgame upgrade

---

## Radiation Damage

**Biological (dose in Sieverts, Sv):**

| Dose | Effects |
|------|---------|
| < 0.1 Sv | No immediate symptoms, slight cancer risk increase |
| 1–2 Sv | Nausea, reduced white blood cells, fatigue |
| 2–6 Sv | Hair loss, bleeding, ~50% fatal at 4–5 Sv without treatment |
| 6–10 Sv | Severe GI damage, very high fatality |
| > 10 Sv | CNS syndrome, rapid incapacitation, near-certain death |

**Material damage:**
- Neutron bombardment displaces atoms in steel → embrittlement over years
- Polymers degrade rapidly under radiation
- Reactor vessels have finite neutron fluence lifetime

**Environmental contamination — key isotopes:**

| Isotope | Half-life | Hazard |
|---------|-----------|--------|
| I-131 | 8 days | Thyroid (short-term, after accident) |
| Cs-137 | 30 years | Long-term ground contamination |
| Sr-90 | 29 years | Bone (acts like calcium in biology) |
| Pu-239 | 24,100 years | Alpha emitter, inhalation hazard |

**Mod potential:**
- Player Sievert counter (accumulated dose)
- Progressive debuffs: nausea → slowness → weakness → blindness → wither
- Contaminated blocks/fluids that passively dose nearby players
- Lead armor or hazmat suit for gamma shielding
- "Internal contamination" mechanic if player is near alpha source without suit

---

## Decay, Half-Life & Spent Fuel Stages

**Decay chain from U-238 in reactor:**  
U-238 + n → U-239 (23.5 min) → Np-239 (2.36 days) → **Pu-239** (24,100 yr) → Pu-240 → Pu-241 (14.4 yr) → Am-241 (432 yr)

**Spent fuel radioactivity over time:**
- Hours–days: short-lived fission products dominate (I-131, Xe-133)
- Months–years: medium-lived (Cs-137, Sr-90)
- Centuries: actinides (Pu-239, Am-241)
- Stays above background for ~100,000 years

**Rule of thumb:** after 7 half-lives, less than 1% of original activity remains.

**Mod potential:**
- Spent rods cycle through stages with different radiation type/intensity
- Reprocessing multiblock (centrifuge): extracts Pu-239 from spent fuel
- Pu-239 usable as MOX fuel (higher power density) or for weapons
- Deep underground waste storage as a late-game mechanic / consequence
- Am-241 as a byproduct with unique properties

---

## Energy & Units

**Energy density comparison:**
- 1 kg U-235 fully fissioned: ~83 TJ (23 GWh)
- 1 metric ton coal: ~8 MWd
- **Nuclear is ~3 million times more energy-dense than coal**

**Thermal vs electric:**
- Reactor output = thermal power (MWt)
- Turbine converts heat to electricity at ~33% efficiency for water-cooled, up to ~45% for gas-cooled
- The cooling system must remove ALL thermal power, not just the electrical fraction

**Mod potential:**
- Separate "thermal power" (heat units/tick) from "electrical power" (FE/t or RF/t)
- Turbine efficiency depends on coolant temperature — sodium or helium coolants unlock better turbines
- Fuel is extremely compact, justifying the complexity/hazard of building a reactor vs coal

---

## The Core Feedback Loop (Most Important)

The most interesting mechanic is the **cascade of consequences**:

```
More rods → more heat
  → coolant must keep up
    → coolant fails
      → temperature spikes
        → cladding ruptures
          → fission products enter coolant
            → contamination spreads through pipes
              → area becomes irradiated
                → players take damage, blocks activate
```

This requires **active management** and rewards players who understand the physics.
Combining burnup + xenon poisoning + decay heat + contamination spread creates a system with genuine depth.
