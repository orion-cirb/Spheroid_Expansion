# Spheroid_Expansion

* **Developed for:** Florian
* **Team:** Monnot
* **Date:** June 2023
* **Software:** Fiji

### Images description

3D images of spheroids taken with a 10x or a x20 objective

2 channels:
1.  GFP: Phalloidin spheroid and protrusions
2.  DAPI: DAPI nuclei

### Plugin description

* Z-project stack over max intensity
* Detect spheroid with median filtering + thresholding and fit a circle on it
* Detect spheroid and protrusions with subtract background + median filtering + thresholding
* Detect nuclei with Stardist
* Perform Sholl analysis: measure area of protrusions and number of nuclei between each two concentric circles

### Dependencies

* **3DImageSuite** Fiji plugin
* **Stardist** conda env + *StandardFluo.zip* model

### Version history

Version 1 released on Jun 30, 2023.
