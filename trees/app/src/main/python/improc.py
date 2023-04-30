import io
import numpy as np
from skimage import io as skio
from skimage import img_as_ubyte, img_as_float, transform
import matplotlib.pyplot as plt

import processor
import model

def run(depth_arr, rgb_arr, rgb_width, rgb_height, depth_width, depth_height):

    TOF_SHAPE = (depth_height, depth_width)
    RGB_SHAPE = (rgb_height, rgb_width)

    # print(f"TOF_SHAPE: {TOF_SHAPE}")
    # print(f"RGB_SHAPE: {RGB_SHAPE}")

    # Read depth image and resize as if we were going to call processor.py
    depth = np.array(depth_arr, dtype=np.float64).reshape(TOF_SHAPE)
    depth = transform.resize(depth, RGB_SHAPE)
    rgb = np.reshape(skio.imread(io.BytesIO(rgb_arr)), RGB_SHAPE) # check if it works
    preds = model.run(depth) # preds is (128, 160)
    preds = transform.resize(preds, RGB_SHAPE) # resize
    modified_depth = np.where(preds==0, 0, depth) # applies mask to depth

    angle, left, right, est_depth, est_width = processor.process(modified_depth, rgb)

	# Prepare display image
    bounds = np.zeros(RGB_SHAPE)
    bounds[:,left] = 1
    bounds[:,right] = 1
    bounds_rot = interpolation.rotate(bounds, -angle, reshape=False)
    rgb_disp = np.append(rgb, modified_depth[:,:,np.newaxis], axis=2)
    rgb_disp[np.where(np.abs(bounds_rot) > 0.1 )] = [0.0, 1.0, 0.0, 1.0]

    return bytes(img_as_ubyte(rgb_disp)), est_depth, est_width
