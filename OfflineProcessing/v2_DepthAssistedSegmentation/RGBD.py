
import argparse
import matplotlib
import matplotlib.image as image
import matplotlib.pyplot as plt
import numpy as np
import os
import pandas as pd

from PIL import Image
from skimage.transform import resize 
from scipy.ndimage.interpolation import rotate
from sys import argv, exit
from termcolor import colored

import processor

##### file reading #####
SAMPLE_PREFIX = "Capture_Sample_"

##### setting calibration parameters #####
SHAPE = (360, 480)
TOF_SHAPE = (180, 240)
CENTER_BOUNDS = (int(SHAPE[1]/3), 2 * int(SHAPE[1]/3))

# read and format rgb and depth images
def get_data(sample_num, capture_num):
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
    conf_matrix = np.array(data_conf).reshape(TOF_SHAPE)
    depth_matrix = np.array(data_depth).reshape(TOF_SHAPE)

    scale_factor = 2
    conf_matrix = np.kron(conf_matrix, np.ones((scale_factor, scale_factor)))
    depth_matrix = np.kron(depth_matrix, np.ones((scale_factor, scale_factor)))
    return img_rgb, conf_matrix, depth_matrix

# read file containing reference widths
def get_widths(reference_file):
    sample_num_to_width = {}
    with open(reference_file) as fp: 
        lines = fp.readlines() 
        for i,line in enumerate(lines): 
            parse = line.split(':')
            if len(parse) != 2:
                raise ValueError('Error reading reference file on line {}: [{}]'.format(i, line))
            sample_num_to_width[int(parse[0])] = float(parse[1])
    return sample_num_to_width


# processes one sample capture
def run_sample(sample_num, capture_num, width):

    ####### PROCESS THE SAMPLE ##########
    # get rgb image and corresponding depth data from files
    img_rgb, conf_matrix, depth_matrix = get_data(sample_num, capture_num)

    try:
        angle, left, right, estimated_depth, estimated_width = processor.process(depth_matrix, img_rgb)
    except processor.MissingDepthError as e:
        print(colored("Unable to process sample {}_{}, no depth values found".format(sample_num, capture_num), "red"))
        return

    center_depths = depth_matrix[:, CENTER_BOUNDS[0]:CENTER_BOUNDS[1]]
    # create rgb-d image with filtered depth values
    # note that the fourth axis in the rgb image will be interpreted as an alpha channel,
    # so all the zero-valued depths will be completely transparent (aka invisible)
    # when the rgb image is shown.
    depth_approx = 0.1 * estimated_depth
    depth_matrix_filtered = np.copy(depth_matrix)
    depth_matrix_filtered[np.abs(depth_matrix - estimated_depth) > depth_approx] = 0.0
    img_rgbd = np.append(img_rgb, depth_matrix_filtered[:,:,np.newaxis], axis=2)

    # bounds = np.zeros(SHAPE)
    # bounds[:,left] = 1
    # bounds[:,right] = 1
    # bounds_rot = rotate(bounds, -angle, reshape=False)
    # rgb_disp2 = np.copy(img_rgbd)
    # rgb_disp2[np.where(np.abs(bounds_rot) > 0.1 )] = [0.0, 1.0, 0.0, 1.0]

    # img_rgbd_rotated = rotate(img_rgbd, angle, reshape=False)
    # rgb_disp = np.copy(img_rgbd_rotated)
    # rgb_disp[:,left-1:left+1] = [1.0, 0.0, 0.0, 1.0]
    # rgb_disp[:,right-1:right+1] = [1.0, 0.0, 0.0, 1.0]
    # rgb_disp = rotate(rgb_disp, -angle, reshape=False)

    error = estimated_width - width
    error_pst = abs(error) / width * 100

    ###### CREATE FIGURE OF RESULTS #########
    # mask out the invalid entries
    mask = np.logical_or(np.logical_or(depth_matrix == 0, depth_matrix == 0.0), depth_matrix > 4.5)
    fig = plt.figure(0,(15,15))

    ax = fig.add_subplot(221)
    # ax.axvline(x=CENTER_BOUNDS[0])
    # ax.axvline(x=CENTER_BOUNDS[1])
    ax.imshow(img_rgb)
    cs = ax.matshow(np.ma.masked_array(depth_matrix, mask))  # colors the points by depth
    fig.colorbar(cs)

    ax = fig.add_subplot(222)
    depth_matrix_center_values = np.ndarray.flatten(center_depths[center_depths != 0])
    ax.hist(depth_matrix_center_values)
    ax.set_title("center depth distribution")

    ax = fig.add_subplot(223)
    ax.axvline(x=left)
    ax.axvline(x=right)
    ax.imshow(rotate(img_rgbd, angle, reshape=False))

    ax = fig.add_subplot(224)
    table_data = [
        ["sample num: ", sample_num],
        ["capture num: ", capture_num],
        ["measured depth (m): ", estimated_depth],
        ["rotate angle (deg): ", angle],
        ["width (m): ", width],
        ["estimated width (m): ", estimated_width],
        ["error: ", error],
        ["error %", error_pst],
    ]
    table = ax.table(cellText=table_data, loc='center', cellLoc='left')
    table.set_fontsize(14)
    table.scale(1, 4)
    ax.axis('off')

    plt.savefig(os.path.join(RESULT_PATH, SAMPLE_PREFIX + "{0}_{1}_Result".format(sample_num, capture_num)))
    plt.close()

    result_dict = {
        "sample_number": sample_num,
        "capture_number": capture_num,
        "trunk_depth_m": estimated_depth,
        "reference_width_m": width,
        "estimated_width_m": estimated_width,
        "angle_deg": angle,
        "error_m": error,
        "percent_error": error_pst,
    }
    return result_dict


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description='Process output of RGB+D Depth-Assisted Segmentation app')
    parser.add_argument('sample_path', type=str, help='path to sample directory')
    parser.add_argument('reference_file', type=str, help=('Path to file containing reference widths for each sample number.\n'
                                                          'File should be formatted as a newline-separate list of \n'
                                                          'sample_num: width_in_meters\n'
                                                          'eg:\n\n'
                                                          '1: .45\n'
                                                          '2: .26\n\n'
                                                          'Note that the widths should be in meters and only the sample number is\n'
                                                          'required. All captured images in a sample will use the same reference width.\n'
                                                          ))
    parser.add_argument('result_path', type=str, help='path to directory in which to store results. this program will overwrite files currently in the results directory.')
    args = parser.parse_args()
    SAMPLE_PATH = args.sample_path
    RESULT_PATH = args.result_path
    REFERENCE_FILE = args.reference_file

    sample_num_to_capture_num = {}  # for mapping samples to captures

    # fetch samples from directory
    file_names = os.listdir(SAMPLE_PATH)
    file_names = [name for name in file_names if name.startswith(SAMPLE_PREFIX) and not (name.endswith('.txt') or name.endswith('.jpeg'))]
    if len(file_names) < 1:
        print(colored("Error: No samples found in directory {}".format(SAMPLE_PATH), "red"))
        exit(1)
    for name in file_names:
        name_split = name.split('_')
        sample_num = int(name_split[-2])
        capture_num = int(name_split[-1])
        if sample_num not in sample_num_to_capture_num:
            sample_num_to_capture_num[sample_num] = []
        sample_num_to_capture_num[sample_num].append(capture_num)

    sample_num_to_width = get_widths(REFERENCE_FILE)


    out = pd.DataFrame(columns=[
        "sample_number",
        "capture_number",
        "trunk_depth_m",
        "reference_width_m",
        "estimated_width_m",
        "angle_deg",
        "error_m",
        "percent_error",
        ])
    # run tests on samples
    for sample_num in sample_num_to_width:
        if sample_num in sample_num_to_capture_num:
            for capture_num in sample_num_to_capture_num[sample_num]:
                print(sample_num, capture_num)
                result_dict = run_sample(sample_num, capture_num, sample_num_to_width[sample_num])
                out = out.append(result_dict, ignore_index=True)

    summary_path = os.path.join(RESULT_PATH, 'results.csv')
    out.to_csv(summary_path)
    print(colored("Successfully processed samples. Check log for errors. Summary results can be found in {}".format(summary_path), "green"))

