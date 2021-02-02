
import io
import math
import numpy as np
import PIL.Image as Image
from scipy.ndimage.interpolation import rotate
from scipy.stats import mode
from sklearn.decomposition import PCA

SHAPE = (360, 480)
TOF_SHAPE = (180, 240)
RGB_SHAPE = (640, 480)
CENTER_BOUNDS = (int(SHAPE[1]/3), 2 * int(SHAPE[1]/3))

CALIB_DEPTH = 1.0  # Units in m
CALIB_PIXEL_PER_METER = 356.25  # Units in m
WIDTH_SCALE_FACTOR = 1.0  # scale the width based on current observation, may not be the most accurate


class Error(Exception):
	"""Base class for exceptions in this module."""
	pass

class MissingDepthError(Error):
	"""Exception raised when the capture has insufficient depth values."""
	message = "Unable to process image, no depth points found"

def find_boundaries(rgbd, depth_filtered):
	angle = get_rotate_angle(rgbd[:,CENTER_BOUNDS[0]:CENTER_BOUNDS[1],:])

	depth_filtered_center_mask = (depth_filtered[:, CENTER_BOUNDS[0]:CENTER_BOUNDS[1]] > 0) * 1
	depth_filtered_center_mask_rotated = rotate(depth_filtered_center_mask, angle)

	depth_filtered_rotated = rotate(depth_filtered, angle)

	left_boundary = 0
	for j in range(int(SHAPE[1] / 3)):
		if np.bincount(depth_filtered_center_mask_rotated[:,j]).argmax() == 1:
			left_boundary = j + int(SHAPE[1] / 3)
			break

	right_boundary = 0
	for j in range(int(SHAPE[1] / 3) - 1, -1, -1):
		if np.bincount(depth_filtered_center_mask_rotated[:,j]).argmax() == 1:
			right_boundary = j + int(SHAPE[1] / 3)
			break

	return angle, left_boundary, right_boundary

# rotates the matrix to vertical based on binary encoding
def get_rotate_angle(matrix):
	x = np.array(np.where(matrix[:,:,3] > 0)).T
	# Perform a PCA and compute the angle of the first principal axes
	pca = PCA(n_components=2).fit(x)
	angle = np.arctan2(*pca.components_[0])
	angle = np.rad2deg(angle)
	if 80 < abs(angle) < 100:  # in case to rotate the image by 90 degree
		angle_1 = angle - 90
		angle_2 = angle + 90
		return angle_1 if abs(angle_1) <= abs(angle_2) else angle_2
	return angle + 180


# calculate the final width
def get_estimated_width(depth, pixels, angle):
    return abs((depth * pixels) / (CALIB_DEPTH * CALIB_PIXEL_PER_METER * math.cos(np.deg2rad(angle)))) * WIDTH_SCALE_FACTOR

def run(depth_arr, rgb_arr):

	# Read images and resize so they can be directly overlaid.
	depth = np.array(depth_arr, dtype=np.float64).reshape(TOF_SHAPE) 
	scale_factor = 2
	depth = np.kron(depth, np.ones((scale_factor, scale_factor)))

	rgb = Image.open(io.BytesIO(rgb_arr)).resize(SHAPE)

	# get filtered depth data and filtered rgb-d image
	# depth data from center third of image
	center_depths = depth[:, CENTER_BOUNDS[0]:CENTER_BOUNDS[1]]
	if np.sum(center_depths) == 0:
		raise MissingDepthError

	# Sensor range gives us a maximum of 5 meters away,
	# For now we'll try 3cm resolution
	bins = np.arange(0.0, 5.0, 0.03)
	digitized_center_depths = np.digitize(center_depths[center_depths != 0], bins)
	mode_depth = bins[mode(digitized_center_depths, axis=None).mode[0]]

	# zero out depth values that are not within 10% of the mode center depth
	depth_approx = 0.1 * mode_depth
	depth_filtered = np.copy(depth)
	depth_filtered[np.abs(depth - mode_depth) > depth_approx] = 0.0

	# create rgb-d image with filtered depth values
	# note that the fourth axis in the rgb image will be interpreted as an alpha channel,
	# so all the zero-valued depths will be completely transparent (aka invisible)
	# when the rgb image is shown.
	rgb_matrix = np.transpose(np.array(rgb), (1, 0, 2))
	rgbd = np.append(rgb_matrix, depth_filtered[:,:,np.newaxis], axis=2)

	# rotate image to fit the tree vertically and approximate with vertical lines
	angle, left, right = find_boundaries(rgbd, depth_filtered)
	rgb_rotated = rgb.rotate(angle)

	# calculate the final estimated width
	estimated_width = get_estimated_width(mode_depth, right - left, angle)

	# Add alpha channel
	rgb.putalpha(255)

	return bytes(rgb.tobytes())

