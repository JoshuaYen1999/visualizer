# Visualizer for the Functional Map of the World Challenge

The purpose of the visualizer application is to let you view object bounding boxes overlaid on satellite images, compare truth to solution annotations, and calculate your solution's score.  
Download the [visualizer package](http://www.topcoder.com/contest/problem/FunctionalMap/visualizer-1.0.zip) and unpack it anywhere on your system.  
Open a command window in the directory where you unzipped the package and execute

```shell
java -Xmx2g -jar visualizer.jar 
     -data-dir <data_directory> 
     -solution <solution_file> 
```

_(The above is a single line command, line breaks are only for readability. The -Xmx2g parameter gives 2GB RAM available for Java. If you have more RAM and you work with lots of data then it is recommended to set this value higher.)_  

This assumes that you have Java (at least v1.7) installed and it is available on your path. The meaning of the above parameters are the following:

*   -data-dir : specifies the base directory of the data files, i.e. the satellite images and meta data files. **Note that the tool supports only the images of the <tt>fmow-rgb</tt> data set, i.e. RGB images in .jpg format.**
*   -solution : your solution file, see ./data/solution.txt for an example. This parameter is optional.

All file and directory parameters can be relative or absolute paths.  

Other optional parameters you may use:

*   -w <width> : width of the tool's screen. Defaults to 1500.
*   -no-gui : if present then no GUI will be shown, the application just scores the supplied solution file in command line mode.
*   -no-ms : if present then the tool will use the xxx_rgb.jpg and xxx_rgb.json files. By default the tool uses the xxx_msrgb.jpg and xxx_msrgb.json files. Note that some of the xxx_rgb.jpg files are large and opening them will take longer.
*   -max-per-cat <N> : the tool uses at most N scenes per object category. This works only if the data files are arranged in the way training data is present in the challenge's data set, where folder names correspond to object labels. By default all images are used.
*   -scene-filter : a regular expression to narrow the list of scenes to be used. E.g. a filter like <tt>crop_field_[89]</tt> will make the tool load only those scenes that have 'crop_field_8' or 'crop_field_9' in their name, like 'crop_field_876'. By default this filter is empty, all images are used.
*   -toc : if present then a toc.txt file will be created in the supplied base directory (-data-dir). When the tool runs the next time, scenes will be loaded from this TOC file, which will make the startup time much faster. When this option is present then all other options are ignored with the exception of -data-dir and -no-ms. No GUI is shown and no scoring is performed.

**Examples**

This command runs the tool with the small set of images that come pre-packaged with it:

```shell
java -jar visualizer.jar -data-dir ./data/ -solution solution.txt
```

This command will show the fmow-rgb training data set, using a maximum of 5 bounding boxes per category:

```shell
java -jar visualizer.jar -data-dir c:/fmow/data/fmow-rgb/train -max-per-cat 5
```

This assumes that you have already downloaded the training data from the fmow-rgb AWS bucket or torrent (see the [problem statement](https://community.topcoder.com/longcontest/?module=ViewProblemStatement&rd=16996&pm=14684) for details) into a similar directory structure as it is in the bucket.  

This command creates a TOC file for the pre-packaged sample data set.

```shell
java -jar visualizer.jar -data-dir ./data/ -toc
```

### Operations

Image selection works by clicking on lines containing an image name in the output log window. Temporal views of the same scene are rolled up into a single line, so e.g. if a scene with ID=airport_0 has three different images taken at different times (with temporal IDs 0, 1 and 4 (note they are not necessarily continuous)) then the corresponding line will show:  

```
    airport_0 _0 _1 _4
```

Clicking at _4 will open image with ID=airport_0_4.  
You can zoom in/out within the image view by the mouse wheel, and pan the view by dragging.  
If truth annotations are present in the meta data files and also a solution file is specified then solution and truth are compared automatically, scores are displayed in the log window and also in the command line. Images that contain error will be marked in the output log window by a '*'.  

### Recommended work flow

*   Download the fmow-rgb data set or a subset of it.
*   Run the tool once in -toc mode to create a toc.txt file in the base directory of the data set. (Note that the tool can work without a TOC file but if you have lots of data then you can significantly reduce launch time by creating one.)
*   Use the tool with the -scene-filter and/or -max-per-cat parameters to view a subset of the data, optionally specifying your solution file as well for scoring.
*   If your data changes, always recreate the TOC file.