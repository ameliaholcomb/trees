
import io
import numpy as np
from skimage import io as skio
from skimage import img_as_ubyte, transform
from scipy.ndimage import interpolation

import processor


# TODO these need to be changed to correct resolution
SHAPE = (360, 480)
TOF_SHAPE = (180, 240)
RGB_SHAPE = (640, 480)


def run(depth_arr, rgb_arr):

	# Read images and resize so they can be directly overlaid.
	depth = np.array(depth_arr, dtype=np.float64).reshape(TOF_SHAPE) 
	scale_factor = 2
	depth = np.kron(depth, np.ones((scale_factor, scale_factor)))

	rgb = transform.resize(skio.imread(io.BytesIO(rgb_arr)), SHAPE)

    try:
	    angle, left, right, est_depth, est_width = processor.process(depth, rgb)
	except (processor.NoTrunkFoundError, processor.MissingDepthError) as e:
	    return bytes(img_as_ubyte(rgb_disp)), 0, 0

	# Prepare display image 
	bounds = np.zeros(SHAPE)
	bounds[:,left] = 1
	bounds[:,right] = 1
	bounds_rot = interpolation.rotate(bounds, -angle, reshape=False)
	rgb_disp = np.append(rgb, depth[:,:,np.newaxis], axis=2)
	rgb_disp[np.where(np.abs(bounds_rot) > 0.1 )] = [0.0, 1.0, 0.0, 1.0]

	# img_rgbd_rotated = interpolation.rotate(rgbd, angle, reshape=False)
	# rgb_disp = np.copy(img_rgbd_rotated)
	# rgb_disp[:,left-1:left+1] = [1.0, 0.0, 0.0, 1.0]
	# rgb_disp[:,right-1:right+1] = [1.0, 0.0, 0.0, 1.0]
	# rgb_disp = interpolation.rotate(rgb_disp, -angle, reshape=False)
	rgb_disp = np.clip(rgb_disp, 0, 1)

	# TODO: pytype
	# Java expects: byte[] displayImage, float estDepth, float estWidth
	return bytes(img_as_ubyte(rgb_disp)), est_depth, est_width

