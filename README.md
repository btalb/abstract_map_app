<p align=center><strong>~ Please see the <a href="https://btalb.github.io/abstract_map/">abstract map site</a> for further details about the research publication ~</strong></p>

# App for the Human vs Abstract Map Zoo Experiments 

![A demo of the app human participants used](./docs/abstract_map_app.gif)

This repository contains the mobile application used by human participants in the zoo experiments described in our [IEEE TCDS journal](https://doi.org/10.1109/TCDS.2020.2993855). The app, created with Android Studio, includes the following:

- opening screen for users to select experiment name & goal location
- live display of the camera to help users correctly capture a tag
- instant visual feedback when a tag is detected, with colouring to denote whether symbolic spatial information is not the goal (red), navigation information (orange), or the goal (green)
- experiment definitions & tag mappings are creatable via the same XML style used in the [abstract_map](https://github.com/btalb/abstract_map) package
- integration with the [native C AprilTags](https://github.com/AprilRobotics/apriltag) using the Android NDK

## Developing & producing the app

The project should be directly openable using Android Studio. 

Please keep in mind that this app was last developed in 2019, and Android Studio often introduces minor breaking changes with new versions. Often you will have to tweak things like Gradle versions / syntax etc. to get a project working with newer versions. Android Studio is very good though with pointing out where it sees errors and offering suggestions for how to resolve them.

Once you have the project open, you should be able to compile the app and load it directly onto a device without issues.

## Acknowledgements & Citing our work

This work was supported by the Australian Research Council's Discovery Projects Funding Scheme under Project DP140103216. The authors are with the [QUT Centre for Robotics](https://research.qut.edu.au/qcr/).

If you use this software in your research, or for comparisons, please kindly cite our work:

```
@ARTICLE{9091567,  
    author={B. {Talbot} and F. {Dayoub} and P. {Corke} and G. {Wyeth}},  
    journal={IEEE Transactions on Cognitive and Developmental Systems},   
    title={Robot Navigation in Unseen Spaces using an Abstract Map},   
    year={2020},  
    volume={},  
    number={},  
    pages={1-1},
    keywords={Navigation;Robot sensing systems;Measurement;Linguistics;Visualization;symbol grounding;symbolic spatial information;abstract map;navigation;cognitive robotics;intelligent robots.},
    doi={10.1109/TCDS.2020.2993855},
    ISSN={2379-8939},
    month={},}
}
```
