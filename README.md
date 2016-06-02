# EmbyMilkVRLinkGenerator

Simple script that scans Emby server for video files and generates mvrl 
files consumable by MilkVR app for Gear VR. Copy generated files to your 
phone's MilkVR directory and you can stream videos directly from your 
server!

It supports tags for automatically supplying 3d format for MilkVR 
player, just tag the video or folder containing the video with one of 
supported formats in emby metadata editor and the script will pick it 
up.

Supported tags are:

```
"_2dp", "_3dpv", "_3dph", "180x180", "180x101", "_mono360",
"3dv", "_tb", "3dh", "_lr", "180x180_3dv", "180x180_3dh",
"180x180_squished_3dh", "180x160_3dv", "180hemispheres",
"cylinder_slice_2x25_3dv", "cylinder_slice_16x9_3dv",
"sib3d", "_planetarium", "_fulldome", "_v360", "_rtxp"
```

For more info including what those formats actually look like see 
[MilkVR faq](https://samsungmilkvr.com/portal/content/faq#video-types)

The script has a few more useful options, here is the usage message that
 should be self explanatory:

```
Usage: <main class> [options]
  Options:
    -h, --help
       Default: false
    -p, --password
       Emby server user password
       Default: <empty string>
    -x, --prefix
       Short prefix for generated mvrl files
       Default: <empty string>
    -s, --server
       Emby server url
       Default: http://192.168.0.1:8096
    -d, --serverDirectory
       Server library/directory/collection name regex
       Default: .*
    -T, --threads
       Number of network threads (set to 20-100 for slow connections)
       Default: 5
    -u, --user
       Emby server username
       Default: emby
    -v, --verbose
       Verbose output (prints network request/responses)
       Default: false
    -t, --videoType
       MilkVR video type
       Default: _2dp
```

Note: script **requires** java 8 to run