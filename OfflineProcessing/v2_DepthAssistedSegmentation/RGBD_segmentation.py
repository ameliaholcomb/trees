import numpy as np
import matplotlib
import matplotlib.image as image
import matplotlib.pyplot as plt
import math
import re, glob
from mpl_toolkits.mplot3d import Axes3D
from PIL import Image
import skimage
import skimage.segmentation as seg
import skimage.filters as filters
from skimage.restoration import (denoise_tv_chambolle, denoise_bilateral,
                                 denoise_wavelet, estimate_sigma)
from skimage.transform import rescale, resize
from scipy.ndimage.interpolation import rotate
from sklearn.decomposition.pca import PCA
from sklearn.cluster import KMeans
from scipy.spatial import ConvexHull
from collections import defaultdict


##### getting raw data #####
SHAPE = (360, 480)
TOF_SHAPE = (180, 240)
TOF_CAM_DIMS = (4.896, 6.528)  # Units are in mm
TOF_FOCAL_LEN = 5

# Sample/Capture: FILL THESE OUT
# sample_num = 1
# capture_num = 1429
sample_num = 2
capture_num = 1431

data_file = ".\samples_20200728\Capture_Sample_{0}_{1}".format(sample_num, capture_num)
image_file = ".\samples_20200728\Capture_Sample_{0}_{1}.jpeg".format(sample_num, capture_num)


##### Process RGB data #####
img_rgb = resize(image.imread(image_file), SHAPE)


##### process depth data #####
data = np.genfromtxt(data_file, delimiter=',')
data_x = data[:, 0].astype(int)
data_y = data[:, 1].astype(int)
data_depth = data[:, 2]
data_conf = data[:, 3]

# create matrix encoding of conf and depth
conf_matrix = np.full((TOF_SHAPE[0], TOF_SHAPE[1]), 0.0)
depth_matrix = np.full((TOF_SHAPE[0], TOF_SHAPE[1]), 0.0)

for i in range(len(data)):
    conf_matrix[data_y[i], data_x[i]] = data_conf[i]
    depth_matrix[data_y[i], data_x[i]] = data_depth[i]

scale_factor = 2
conf_matrix = np.kron(conf_matrix, np.ones((scale_factor, scale_factor)))
depth_matrix = np.kron(depth_matrix, np.ones((scale_factor, scale_factor)))

# collect center depth data
depth_matrix_center_values = []

for i in range(SHAPE[0]):
    for j in range(int(SHAPE[1] / 3)):
        depth = depth_matrix[i][j + int(SHAPE[1] / 3)]
        if depth != 0:
            depth_matrix_center_values.append(depth)

# find maximum value occurance
depth_matrix_center_values_dict = defaultdict(int)
for i in depth_matrix_center_values:
    depth_matrix_center_values_dict[i] += 1
most_occur_depth = max(depth_matrix_center_values_dict.items(), key=lambda x: x[1])
print(most_occur_depth)

# filter depth values, then create RGBD and convex hull
depth_mid = most_occur_depth[0]
depth_approx = 0.1
depth_min = depth_mid * (1 - depth_approx)
depth_max = depth_mid * (1 + depth_approx)

depth_matrix_filtered = np.full((SHAPE[0], SHAPE[1]), 0.0)
for i in range(SHAPE[0]):
    for j in range(SHAPE[1]):
        if depth_min <= depth_matrix[i][j] <= depth_max:
            depth_matrix_filtered[i][j] = depth_matrix[i][j]

img_rgbd = np.full((SHAPE[0], SHAPE[1], 4), 0.0)
for i in range(SHAPE[0]):
    for j in range(SHAPE[1]):
        img_rgbd[i][j] = [img_rgb[i][j][0], img_rgb[i][j][1], img_rgb[i][j][2], depth_matrix_filtered[i][j]]

depth_matrix_center_coordinates_x = []
depth_matrix_center_coordinates_y = []
for i in range(SHAPE[0]):
    for j in range(int(SHAPE[1] / 3)):
        if depth_matrix_filtered[i][j + int(SHAPE[1] / 3)] != 0.0:
            depth_matrix_center_coordinates_x.append(j + int(SHAPE[1] / 3))
            depth_matrix_center_coordinates_y.append(i)
depth_matrix_center_coordinates_y = np.array(depth_matrix_center_coordinates_y)
depth_matrix_center_coordinates_x = np.array(depth_matrix_center_coordinates_x)

##### Compute convex hull polygon with Graham Scan algorithm #####
# def get_cross_product(p1, p2, p3):
#     return ((p2[0] - p1[0])*(p3[1] - p1[1])) - ((p2[1] - p1[1])*(p3[0] - p1[0]))
#
# def get_slope(p1, p2):
#     if p1[0] == p2[0]:
#         return float('inf')
#     else:
#         return (p1[1]-p2[1]) / (p1[0]-p2[0])
#
# def compute_convex_hull(points):
#     hull = []
#     points.sort(key=lambda x:[x[0],x[1]])
#     start = points.pop(0)
#     hull.append(start)
#     points.sort(key=lambda p: (get_slope(p, start), -p[1], p[0]))
#     for pt in points:
#         hull.append(pt)
#         while len(hull) > 2 and get_cross_product(hull[-3],hull[-2],hull[-1]) < 0:
#             hull.pop(-2)
#     return hull
#
# hull = compute_convex_hull(depth_matrix_center_coordinates)
hull = ConvexHull(np.hstack((depth_matrix_center_coordinates_x[:,np.newaxis],
                             depth_matrix_center_coordinates_y[:,np.newaxis])))

# rotate image to fit the tree vertically, then approximate with vertical lines
def get_rotate_angle(matrix):
    x = np.array(np.where(matrix > 0)).T
    # Perform a PCA and compute the angle of the first principal axes
    pca = PCA(n_components=2).fit(x)
    angle = np.arctan2(*pca.components_[0])
    angle_1 = angle/math.pi*180-90
    angle_2 = angle/math.pi*180+90
    return angle_1 if abs(angle_1) <= abs(angle_2) else angle_2

depth_matrix_filtered_center_mask = (depth_matrix_filtered[:, int(SHAPE[1] / 3) : int(SHAPE[1] / 3) * 2] > 0) * 1
angle = get_rotate_angle(depth_matrix_filtered_center_mask)
print(angle)
img_rgbd_rotated = rotate(img_rgbd,angle)
depth_matrix_filtered_center_mask_rotated = rotate(depth_matrix_filtered_center_mask,angle)

left_boundary = 0
for j in range(int(SHAPE[1] / 3)):
    if np.bincount(depth_matrix_filtered_center_mask_rotated[:,j]).argmax() == 1:
        left_boundary = j + int(SHAPE[1] / 3)
        break

right_boundary = 0
for j in range(int(SHAPE[1] / 3) - 1, -1, -1):
    if np.bincount(depth_matrix_filtered_center_mask_rotated[:,j]).argmax() == 1:
        right_boundary = j + int(SHAPE[1] / 3)
        break

print(left_boundary, right_boundary)



##### Display figures #####
# mask out the invalid entries
mask = np.logical_or(depth_matrix == 0, depth_matrix == 0.0)

fig = plt.figure(0,(15,15))

ax = fig.add_subplot(221)
ax.imshow(img_rgb)
cs = ax.matshow(np.ma.masked_array(depth_matrix, mask))  # colors the points by depth
fig.colorbar(cs)

ax = fig.add_subplot(222)
ax.hist(depth_matrix_center_values)

ax = fig.add_subplot(223)
ax.plot(depth_matrix_center_coordinates_x[hull.vertices], depth_matrix_center_coordinates_y[hull.vertices])
ax.imshow(img_rgbd)

ax = fig.add_subplot(224)
ax.axvline(x=left_boundary)
ax.axvline(x=right_boundary)
ax.imshow(img_rgbd_rotated)

plt.show()

######## K mean clustering #############
#
# def segment_slic(im, seg_params):
#     sigma = seg_params['sigma']
#     compactness = seg_params['compactness']
#     n_segments = seg_params['n_segments']
#     multichannel = False
#     return seg.slic(im.astype(np.float64), n_segments=n_segments, sigma=sigma,
#                     compactness=compactness, multichannel=multichannel)
#
#
# def segment_thresh(im):
#     im_thresh = filters.threshold_otsu(im)
#     return im > im_thresh
#
#
# def denoise_tv(im):
#     return denoise_tv_chambolle(im, weight=0.3)
#
#
# sigma = 0 # 0.1
# compactness = 0.001
# n_segments = 8
# seg_params = {
#     'sigma': sigma,
#     'compactness': compactness,
#     'n_segments': n_segments
# }
#
# fig = plt.figure(2, figsize=(14,10))
#
# ax1 = fig.add_subplot(1,2,1)
# ax1.title.set_text('slic')
# ax1.imshow(segment_slic(img_rgbd[:,:], seg_params))
# # ax1.axes.get_xaxis().set_visible(False)
# # ax1.axes.get_yaxis().set_visible(False)
#
# ax2 = fig.add_subplot(1,2,2)
# ax2.title.set_text('otsu w/ tv denoising')
# ax2.imshow(segment_thresh(denoise_tv(depth_matrix[:,:])))
# # ax2.axes.get_xaxis().set_visible(False)
# # ax2.axes.get_yaxis().set_visible(False)
#
# plt.show()














