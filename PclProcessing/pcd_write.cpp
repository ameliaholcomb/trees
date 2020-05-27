#include <fstream>
#include <iostream>
#include <memory>
#include <pcl/common/common_headers.h>
#include <pcl/console/parse.h>
#include <pcl/io/pcd_io.h>
#include <pcl/point_types.h>
#include <boost/tokenizer.hpp>

namespace {
using ::pcl::PointCloud;
using ::pcl::PointXYZ;

// Command usage help
void printUsage (const char* progName)
{
  std::cout << "\n\nUsage: "<<progName<<" [options] <data.txt> <outfile.pcd>\n\n"
            << "Options:\n"
            << "-------------------------------------------\n"
            << "-h           this help text\n"
            << "\n\n";
}

std::unique_ptr<PointCloud<PointXYZ>> makeCloud() {
	auto cloud = std::make_unique<PointCloud<PointXYZ>>();

	// dimensions of the tof image
	cloud->width = 180;
	cloud->height = 240;
	// We may have NaN values in data set
	cloud->is_dense = false;
	cloud->points.resize(cloud->width * cloud->height);

	// Set sensor location to world coordinate center
	cloud->sensor_origin_.setZero();
	cloud->sensor_orientation_.w() = 0.0f;
	cloud->sensor_orientation_.x() = 1.0f;
	cloud->sensor_orientation_.y() = 0.0f;
	cloud->sensor_orientation_.z() = 0.0f;
	return cloud;
}

// Use camera intrinsic parameters to transform points from u,v,depth
// into real-world xyz coordinates. (Depth sensor is assumed at (0,0,0))
pcl::PointXYZ pointXYZFromDepth(float u, float v, float depth) {

	const float bad_point = std::numeric_limits<float>::quiet_NaN (); 

	// Camera intrinsic parameters
	const float alph_x = 492.68967;		// focal width / sensor width
	const float alph_y = 492.6062;		// focal height / sensor height
	const float u0 = 323.59485;			// image center 
	const float v0 = 234.65974;			// image center

	pcl::PointXYZ point;

	// depth value of zero is invalid
	if (depth == 0) {
		point.x = point.y = point.z = bad_point;
		return point;
	}

	point.x = depth * ((u - u0) / alph_x);
	point.y = depth * ((v - v0) / alph_y);
	point.z = depth;
	return point;
}

int writePcd(const std::string& filename, const std::string& outfile) {

	// Open the raw file for reading
	std::ifstream infile(filename);
	if (!infile) {
		throw std::runtime_error("File not found.");
	}

	std::unique_ptr<PointCloud<PointXYZ>> cloud = makeCloud();
	int i = 0;
	// File format: u,v,depth,confidence
	std::vector<float> data(4);

	// Read file and insert data into point cloud
	for (std::string line; std::getline(infile, line) && i < cloud->points.size();) {
     	boost::tokenizer<boost::char_separator<char>> tok(line, boost::char_separator<char>(","));
     	data.clear();
     	for (const auto& t: tok) {
     		data.push_back(std::stof(t));	
     	}
     	if (data.size() < 3) {
     		throw std::runtime_error("Incorrect file format on line " + std::to_string(i));
     	}

     	// Transform from u,v,d into x,y,z world coordinates
     	cloud->points[i] = pointXYZFromDepth(data[0], data[1], data[2]);
		i++;
	}

	// Write point cloud to file
	pcl::io::savePCDFileASCII (outfile, *cloud);
	std::cerr << "Saved " << cloud->size() << " data points to test_pcd.pcd" << std::endl;

	return(0);

}
} // namespace

int main (int argc, char** argv) {

	// Parse command-line arguments
  	if (pcl::console::find_argument (argc, argv, "-h") >= 0)
  	{
    	printUsage(argv[0]);
    	return 0;
  	}
  	if (argc != 3) {
  		std::cout << "Incorrect number of arguments.";
  		printUsage(argv[0]);
  		return 0;
  	}
  	std::string filename = std::string(argv[1]);
  	std::string outfile = std::string(argv[2]);

  	return writePcd(filename, outfile);
}