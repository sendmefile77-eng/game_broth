# Third-party notice: embedded Local Dream

The personal non-commercial Android build can compile the image-generation backend from:

- Project: Local Dream
- Source: https://github.com/xororz/local-dream
- Pinned revision: `9ea3c41ed794902a2206f1654defe2549fcdbf62`
- Upstream license: Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)
- License text: https://creativecommons.org/licenses/by-nc/4.0/legalcode

Game Broth modifies the integration/build path for this personal non-commercial build: the Local Dream native backend is compiled against QAIRT/QNN 2.48, packaged into the same APK, started from the game's own private process, and bound only to loopback. Those integration changes are not an upstream Local Dream release and do not imply endorsement by the Local Dream author.

The manual Android workflow obtains Qualcomm AI Runtime (QAIRT/QNN) Community SDK 2.48 from Qualcomm's official download host at build time. QAIRT binaries are not committed to this repository. The produced personal APK contains the runtime files required by the embedded backend.
