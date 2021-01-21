This code depends on the PCL library (pointclouds.org).

As of now, the latest PCL version released for OSX is 1.9.
Unfortunately, this version contains a bug not fixed until 1.10.0.

As a result, on OSX you must 
$ brew install pcl --HEAD
Once https://github.com/Homebrew/homebrew-core/pull/54706 is resolved,
$ brew install pcl
will suffice.