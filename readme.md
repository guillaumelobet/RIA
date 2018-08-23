# Simple root image analysis in ImageJ

## Requirements

Images need to be in black and white, with the roots in black, for a good analysis. If analysing shoot, please rotate the image such as the shoot is upside-down. 

## Installation


- Install [FIJI](https://fiji.sc/)
- Download the file `RIA_J.jar` from [HERE](https://github.com/guillaumelobet/RIA/raw/master/RIA_J.jar)
- Launch FIJI and go to `Plug-in > Install Plugin`
- Select the `RIA_J.jar` file
- Relaunch FIJI

You're done!


## Using the plugin

- Lunch FIJI
- Go to `Plug-ins > RIA`
- A new window will open. Go to the `RIAJ Analysis` tab
- Choose the folder containing your images. You can either copy/paste it in the field, or navigate with the `Choose folder` button. Be careful that the path is correct. 
- Tick the desired options:
  - `Save images`: will save an images illustrating the different analysis (such as the convexhull, shape, ...). This take more time and more space.
  - `Even the tips` : Save an image with the tips detection. Takes even more time (more than double, for some obscur reason to me). 
  - `Verbatim`: print in the log windown the current step perfomed in the analysis
  - `Save TPS`: Save a TPS file, that could be then used for a morphometric analysis. 
- Set the scale
- Set the minimal root size, in pixels
- Choose the export path for the results (as a `csv` file) 
- Click the `Run analysis` button

## Screencast of RIA-J

[![IMAGE ALT TEXT HERE](/img/video.png)](https://www.youtube.com/watch?v=xgBFaJgVdyE)

## Example output

### Root

![example output](/img/output.png)

### Shoot

![example output](/img/output_2.png)


### Butterfly

![example output](/img/output_3.png)


## Cite us

If you use RIA-J please use the following citation:

  Lobet G, Koevoets IT, Noll M, Tocquin P, Meyer PE, Pagès L, et al. Using a structural root system model to evaluate and improve the accuracy of root image analysis pipelines. Front Plant Sci. Frontiers; 2017;8. doi:10.3389/fpls.2017.00447
