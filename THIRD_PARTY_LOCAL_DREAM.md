# Third-party notice: embedded Local Dream

The personal non-commercial Android build can compile the image-generation backend from:

- Project: Local Dream
- Source: https://github.com/xororz/local-dream
- Pinned revision: `9ea3c41ed794902a2206f1654defe2549fcdbf62`
- License in the upstream repository: Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)

Game Broth modifications integrate the Local Dream native backend into the same APK and run it only on loopback. This notice does not imply endorsement by the Local Dream author.

The manual Android workflow obtains the Qualcomm AI Runtime (QAIRT/QNN) Community SDK from Qualcomm's official download host at build time and does not commit QAIRT binaries to this repository. The produced personal APK contains the runtime files required by the embedded backend.
