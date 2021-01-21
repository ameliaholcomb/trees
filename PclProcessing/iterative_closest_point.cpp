#include <iostream>
#include <random>
#include <pcl/console/parse.h>
#include <pcl/io/pcd_io.h>
#include <pcl/point_types.h>
#include <pcl/registration/icp.h>

namespace {
// Command usage help
void printUsage (const char* progName)
{
	std::cout << "\n\nUsage: " << progName << " [options] <cloud_1.pcd> <cloud_2.pcd>\n\n"
	          << "Options:\n"
	          << "-------------------------------------------\n"
	          << "-h           this help text\n"
	          << "\n\n";
}

void runIcp(pcl::PointCloud<pcl::PointXYZ>::Ptr cloud_1, pcl::PointCloud<pcl::PointXYZ>::Ptr cloud_2)
{
	pcl::IterativeClosestPoint<pcl::PointXYZ, pcl::PointXYZ> icp;
	icp.setInputSource(cloud_1);
	icp.setInputTarget(cloud_2);

	pcl::PointCloud<pcl::PointXYZ> final;
	icp.align(final);

	std::cout << "has converged:" << icp.hasConverged() << " score: " <<
	          icp.getFitnessScore() << std::endl;
	std::cout << icp.getFinalTransformation() << std::endl;
}
}  // namespace

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
	std::string file_1 = std::string(argv[1]);
	std::string file_2 = std::string(argv[2]);


	auto cloud_1 = std::make_unique<pcl::PointCloud<pcl::PointXYZ>>();
	auto cloud_2 = std::make_unique<pcl::PointCloud<pcl::PointXYZ>>();
	if (pcl::io::loadPCDFile<pcl::PointXYZ> (file_1, *cloud_1) == -1) {
		throw std::invalid_argument("Couldn't read first point cloud file\n");
	}
	if (pcl::io::loadPCDFile<pcl::PointXYZ> (file_2, *cloud_2) == -1) {
		throw std::invalid_argument("Couldn't read second point cloud file \n");
	}

	runIcp(std::move(cloud_1), std::move(cloud_2));
	return 0;
}