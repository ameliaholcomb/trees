import math
import numpy as np
from scipy.ndimage import interpolation
from scipy import stats
from sklearn.decomposition import PCA

SHAPE = (360, 480)
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
	depth_filtered_center_mask_rotated = interpolation.rotate(depth_filtered_center_mask, angle, reshape=False)

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

	# Angle of the eigenvectors to the horizontal axis
	angles = np.arctan2(*pca.components_)
	angles = np.rad2deg(angles)

	# We are using the eigenvector to define an axis,
	# but adding 180 degrees identifies the same axis.
	# Therefore, we restrict our angle to be between [0, 180]
	# (The tree always points "up" though it may lean left or right.)
	angles = np.mod(angles, 180)

	# PCA returns two eigenvectors.
	# In most cases, the first runs along the principal axis of the trunk.
	# However, sometimes we get the perpendicular eigenvector.
	# We assume that the tree is relatively upright, 
	# and the correct eigenvector is within 60 degrees of vertical.
	# (See also Kour et al.)
	if abs(90 - angles[0]) < 60:
		angle = angles[0]
	else:
		angle = angles[1]

	# Arctan returned an angle from the horizontal axis, but we are looking for
	# the angle off the vertical axis
	# (how far the image must be rotated to make the trunk vertical)
	return 90 - angle


# calculate the final width
def get_estimated_width(depth, pixels, angle):
	return abs((depth * pixels) / (CALIB_DEPTH * CALIB_PIXEL_PER_METER * math.cos(np.deg2rad(angle)))) * WIDTH_SCALE_FACTOR

def process(depth, rgb):
	"""Process depth and rgb image to find trunk diameter.
	Args:
		depth (np.array(360, 480, dtype=np.float64)): ToF image.
		rgb (np.array(360, 480, 3, dtype=np.float64)): RGB image.
	Returns:
		angle (float): rotation angle
		left, right (int): left and right trunk boundaries
		depth (float): estimated depth of trunk
		width (float): estimated trunk diameter
	"""

	# get filtered depth data and filtered rgb-d image
	# depth data from center third of image
	center_depths = depth[:, CENTER_BOUNDS[0]:CENTER_BOUNDS[1]]
	if np.sum(center_depths) == 0:
		raise MissingDepthError

	# Sensor range gives us a maximum of 5 meters away,
	# For now we'll try 3cm resolution
	bins = np.arange(0.0, 5.0, 0.03)
	digitized_center_depths = np.digitize(center_depths[center_depths != 0], bins)
	mode_depth = bins[stats.mode(digitized_center_depths, axis=None).mode[0]]

	# zero out depth values that are not within 10% of the mode center depth
	depth_approx = 0.1 * mode_depth
	depth_filtered = np.copy(depth)
	depth_filtered[np.abs(depth - mode_depth) > depth_approx] = 0.0

	# create rgb-d image with filtered depth values
	# note that the fourth axis in the rgb image will be interpreted as an alpha channel,
	# so all the zero-valued depths will be partly transparent
	# when the rgb image is shown.
	rgbd = np.append(rgb, depth_filtered[:,:,np.newaxis], axis=2)

	# rotate image to fit the tree vertically and approximate with vertical lines
	angle, left, right = find_boundaries(rgbd, depth_filtered)

	# calculate the final estimated width
	estimated_width = get_estimated_width(mode_depth, right - left, angle)

	return angle, left, right, mode_depth, estimated_width

