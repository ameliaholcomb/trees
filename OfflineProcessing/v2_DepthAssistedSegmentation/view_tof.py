import copy
import numpy as np 
import matplotlib.pyplot as plt
from skimage import transform

import argparse

def extract_data(filename):
	# Read in the data
	data = np.genfromtxt(filename, delimiter=',')
	depth_range = data[:,2]

	return data[:,0], data[:,1], depth_range.astype(np.float64)

def main(infile, width, height):
	print('extracting ...')
	x, y, depth = extract_data(infile)
	depth = np.reshape(depth, (width, height))

	print('drawing ...')
	fig = plt.figure()
	ax = fig.add_subplot(111)
		
	palette = copy.copy(plt.cm.viridis)
	palette.set_bad('black')
	mask = np.logical_or(depth == 0, depth > 2)
	ax.matshow(np.ma.masked_array(depth,mask), cmap=palette)
	ax.get_xaxis().set_visible(False)
	ax.get_yaxis().set_visible(False)
	plt.tight_layout()

	plt.savefig(infile + ".jpg")
	print('done!')
	print('file saved as ' + infile + '.jpg')


if __name__ == '__main__':
	parser = argparse.ArgumentParser(
		description="Script to convert depth files to readable view")
	parser.add_argument(
		"-i",
		"--infile",
		help="The name of the depth file to read.",
		type=str,
	)
	parser.add_argument(
		"--width",
		help="Expected TOF image width",
		type=int,
	)
	parser.add_argument(
		"--height",
		help="Expected TOF image height",
		type=int,
	)
	args = parser.parse_args()
	main(args.infile, args.width, args.height)


