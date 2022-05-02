import io
import numpy as np
from skimage import io as skio
from skimage import img_as_ubyte, img_as_float, transform
import matplotlib.pyplot as plt



def run(depth_arr, rgb_arr, rgb_width, rgb_height, depth_width, depth_height):

    TOF_SHAPE = (depth_height, depth_width)
    RGB_SHAPE = (rgb_height, rgb_width)

    print(f"TOF_SHAPE: {TOF_SHAPE}")
    print(f"RGB_SHAPE: {RGB_SHAPE}")

    # Read depth image and resize as if we were going to call processor.py
    depth = np.array(depth_arr, dtype=np.float64).reshape(TOF_SHAPE)
    depth = transform.resize(depth, RGB_SHAPE)

    # Instead of calling processor.py, just return the raw depth image for display.

    # Convert depths to 0-255 integer range
    norm = (255 * (depth - np.min(depth)) / np.ptp(depth)).astype(int)
    # Use 0-255 integers to index into sequential colormap
    c_arr = np.array(plt.cm.viridis.colors)
    color = c_arr[norm]
    # Add an axis of ones (alpha channel = 1)
    # because Java expects an image of type RGB_8888
    # (aka, four bytes per pixel)
    ones = np.ones((color.shape[0], color.shape[1]))
    color = np.append(color, ones[:, :, np.newaxis], axis=2)
    # Return raw bytes and bogus depth and width values
    return img_as_ubyte(color).tobytes(), 1, 1
