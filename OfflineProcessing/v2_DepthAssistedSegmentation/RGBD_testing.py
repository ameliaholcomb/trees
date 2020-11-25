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
import os
import statistics

##### file reading and sample ground truth #####
SAMPLE_PATH = ".\samples_20200728"
RESULT_PATH = ".\\results"
SAMPLE_PREFIX = "Capture_Sample_"

SAMPLE_NUM_TO_WIDTH = {  # this is hardcoded, could be improved using csv files
        1: 0.24,
        2: 0.22,
        3: 0.32,
        4: 0.1,
        5: 0.08,
        6: 0.15,
        7: 0.16,
        8: 0.17,
        9: 0.26,
        10: 0.08,
        11: 0.33,
        12: 0.25,
        13: 0.32,
        14: 0.11,
    }


##### setting calibration parameters #####
SHAPE = (360, 480)
TOF_SHAPE = (180, 240)
TOF_CAM_DIMS = (4.896, 6.528)  # Units are in mm
TOF_FOCAL_LEN = 5

CALIB_DEPTH = 1.0  # Units in m
CALIB_PIXEL_PER_METER = 356.25  # Units in m
WIDTH_SCALE_FACTOR = 1.1  # scale the width based on current observation, may not be the most accurate


# rotates the matrix to vertical based on binary encoding
def get_rotate_angle(matrix):
    x = np.array(np.where(matrix > 0)).T
    # Perform a PCA and compute the angle of the first principal axes
    pca = PCA(n_components=2).fit(x)
    angle = np.arctan2(*pca.components_[0])
    angle = np.rad2deg(angle)
    print(angle)
    if 80 < abs(angle) < 100:  # in case to rotate the image by 90 degree
        angle_1 = angle - 90
        angle_2 = angle + 90
        return angle_1 if abs(angle_1) <= abs(angle_2) else angle_2
    return angle + 180


# calculate the final width
def get_estimated_width(depth, pixels, angle):
    return abs((depth * pixels) / (CALIB_DEPTH * CALIB_PIXEL_PER_METER * math.cos(np.deg2rad(angle)))) * WIDTH_SCALE_FACTOR


# processes one sample capture
def run_sample(sample_num, capture_num, width):
    # getting raw data
    data_file = os.path.join(SAMPLE_PATH, SAMPLE_PREFIX + "{0}_{1}".format(sample_num, capture_num))
    image_file = os.path.join(SAMPLE_PATH, SAMPLE_PREFIX + "{0}_{1}.jpeg".format(sample_num, capture_num))

    # process RGB data
    img_rgb = resize(image.imread(image_file), SHAPE)

    # process depth data
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

    hull = ConvexHull(np.hstack((depth_matrix_center_coordinates_x[:,np.newaxis],
                                 depth_matrix_center_coordinates_y[:,np.newaxis])))

    # rotate image to fit the tree vertically, then approximate with vertical lines
    depth_matrix_filtered_center_mask = (depth_matrix_filtered[:, int(SHAPE[1] / 3) : int(SHAPE[1] / 3) * 2] > 0) * 1
    angle = get_rotate_angle(depth_matrix_filtered_center_mask)
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

    # calculate the final estimated width
    estimated_width = get_estimated_width(depth_mid, right_boundary - left_boundary, angle)
    error = estimated_width - width

    # save figures
    # mask out the invalid entries
    mask = np.logical_or(depth_matrix == 0, depth_matrix == 0.0)

    fig = plt.figure(0,(15,15))

    ax = fig.add_subplot(221)
    ax.imshow(img_rgb)
    cs = ax.matshow(np.ma.masked_array(depth_matrix, mask))  # colors the points by depth
    fig.colorbar(cs)

    ax = fig.add_subplot(222)
    ax.hist(depth_matrix_center_values)
    ax.set_title("center depth distribution")

    # ax = fig.add_subplot(223)
    # ax.plot(depth_matrix_center_coordinates_x[hull.vertices], depth_matrix_center_coordinates_y[hull.vertices])
    # ax.imshow(img_rgbd)

    ax = fig.add_subplot(223)
    ax.axvline(x=left_boundary)
    ax.axvline(x=right_boundary)
    ax.imshow(img_rgbd_rotated)

    ax = fig.add_subplot(224)
    table_data = [
        ["sample num: ", sample_num],
        ["capture num: ", capture_num],
        ["measured depth (m): ", depth_mid],
        ["rotate angle (deg): ", angle],
        ["width (m): ", width],
        ["estimated width (m): ", estimated_width],
        ["error: ", error],
        ["error %", abs(error) / width * 100],
    ]
    table = ax.table(cellText=table_data, loc='center', cellLoc='left')
    table.set_fontsize(14)
    table.scale(1, 4)
    ax.axis('off')

    plt.savefig(os.path.join(RESULT_PATH, SAMPLE_PREFIX + "{0}_{1}_Result".format(sample_num, capture_num)))
    plt.close()

    return error, abs(error) / width * 100





if __name__ == "__main__":
    sample_num_to_capture_num = {}  # for mapping samples to captures

    # fetch samples from directory
    file_names = os.listdir(SAMPLE_PATH)
    file_names = [name for name in file_names if name.startswith(SAMPLE_PREFIX) and not (name.endswith('.txt') or name.endswith('.jpeg'))]
    for name in file_names:
        name_split = name.split('_')
        sample_num = int(name_split[-2])
        capture_num = int(name_split[-1])
        if sample_num not in sample_num_to_capture_num:
            sample_num_to_capture_num[sample_num] = []
        sample_num_to_capture_num[sample_num].append(capture_num)

    errors = []
    error_psts = []

    # run tests on samples
    for sample_num in SAMPLE_NUM_TO_WIDTH:
        if sample_num in sample_num_to_capture_num:
            for capture_num in sample_num_to_capture_num[sample_num]:
                print(sample_num, capture_num)
                try:
                    error, error_pst = run_sample(sample_num, capture_num, SAMPLE_NUM_TO_WIDTH[sample_num])
                    errors.append(error)
                    error_psts.append(error_pst)
                except:
                    None

    # plot the error summary
    fig = plt.figure(0, (15, 15))
    ax = fig.add_subplot(131)
    ax.hist(errors)
    ax.set_title("errors")

    ax = fig.add_subplot(132)
    ax.hist(error_psts, bins=50)
    ax.set_title("error percentages")

    ax = fig.add_subplot(133)
    table_data = [
        ["total sample: ", len(errors)],
        ["mean error: ", statistics.mean(errors)],
        ["median error: ", statistics.median(errors)],
        ["mean error_pst: ", statistics.mean(error_psts)],
        ["median error_pst: ", statistics.median(error_psts)],
    ]
    table = ax.table(cellText=table_data, loc='center', cellLoc='left')
    table.set_fontsize(14)
    table.scale(1, 4)
    ax.axis('off')

    plt.show()


















