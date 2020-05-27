#include <iostream>
#include <memory>

#include <pcl/common/common_headers.h>
#include <pcl/io/pcd_io.h>
#include <pcl/point_types.h>
#include <pcl/range_image/range_image.h>
#include <pcl/visualization/range_image_visualizer.h>
#include <pcl/visualization/pcl_visualizer.h>
#include <pcl/console/parse.h>


namespace {
constexpr float kNoiseLevel = 0.0f;
constexpr float kMinRange = 0.0f;
constexpr int kBorderSize = 1;

// Default image parameters
struct ImageParams
{
	float angular_resolution_x = pcl::deg2rad(0.5f);
	float angular_resolution_y = pcl::deg2rad(0.5f);
	pcl::RangeImage::CoordinateFrame coordinate_frame = pcl::RangeImage::CAMERA_FRAME;
	bool live_update = false;
};

std::ostream& operator<<(std::ostream& ostream, const ImageParams& params)
{
	ostream << "angular_resolution_x: " << params.angular_resolution_x << "rad" << std::endl
	        << "angular_resolution_y: " << params.angular_resolution_y << "rad" << std::endl
	        << "coordinate_frame: " << params.coordinate_frame << std::endl
	        << "live update: " << params.live_update << std::endl;
	return ostream;
}

// Command usage help
void printUsage (const std::string& program_name)
{
	ImageParams params;
	std::cout << "\n\nUsage: " << program_name << " [options] <scene.pcd>\n\n"
	          << "Options:\n"
	          << "-------------------------------------------\n"
	          << "-rx <float>  angular resolution in radians (default " << params.angular_resolution_x << ")\n"
	          << "-ry <float>  angular resolution in radians (default " << params.angular_resolution_y << ")\n"
	          << "-c <int>     coordinate frame (default " << (int)params.coordinate_frame << ")\n"
	          << "-l           live update - update the range image according to the selected view in the 3D viewer. (default " << params.live_update << ")\n"
	          << "-h           this help\n"
	          << "\n\n";
}

void setViewerPose (pcl::visualization::PCLVisualizer* viewer, const Eigen::Affine3f& viewer_pose)
{
	Eigen::Vector3f pos_vector = viewer_pose * Eigen::Vector3f(0, 0, 0);
	Eigen::Vector3f look_at_vector = viewer_pose.rotation () * Eigen::Vector3f(0, 0, 1) + pos_vector;
	Eigen::Vector3f up_vector = viewer_pose.rotation () * Eigen::Vector3f(0, -1, 0);
	viewer->setCameraPosition (pos_vector[0], pos_vector[1], pos_vector[2],
	                           look_at_vector[0], look_at_vector[1], look_at_vector[2],
	                           up_vector[0], up_vector[1], up_vector[2]);
}

std::unique_ptr<pcl::RangeImage> rangeImageFromPointCloud(const pcl::PointCloud<pcl::PointXYZ>& point_cloud, const ImageParams& params)
{
	auto range_image = std::make_unique<pcl::RangeImage>();

	Eigen::Affine3f scene_sensor_pose = Eigen::Affine3f (Eigen::Translation3f (point_cloud.sensor_origin_[0],
	                                    point_cloud.sensor_origin_[1],
	                                    point_cloud.sensor_origin_[2])) *
	                                    Eigen::Affine3f (point_cloud.sensor_orientation_);

	range_image->createFromPointCloud (point_cloud, params.angular_resolution_x, params.angular_resolution_y,
	                                   pcl::deg2rad (360.0f), pcl::deg2rad (180.0f),
	                                   scene_sensor_pose, params.coordinate_frame, kNoiseLevel, kMinRange, kBorderSize);
	return range_image;
}

void runVisualization(pcl::PointCloud<pcl::PointXYZ>::Ptr point_cloud, const ImageParams& params)
{

	pcl::RangeImage::Ptr range_image = rangeImageFromPointCloud(*point_cloud, params);
	std::cout << *range_image << "\n";

	// Open 3D Viewer and add point cloud
	const std::string title = "Range image";
	pcl::visualization::PCLVisualizer viewer ("3D Viewer");
	viewer.setBackgroundColor (1, 1, 1);
	pcl::visualization::PointCloudColorHandlerCustom<pcl::PointWithRange> range_image_color_handler (range_image, 0, 0, 0);
	viewer.addPointCloud (range_image, range_image_color_handler, title);
	viewer.setPointCloudRenderingProperties (pcl::visualization::PCL_VISUALIZER_POINT_SIZE, /*value=*/1, title);

	// viewer.addCoordinateSystem (1.0f, "global");
	// pcl::visualization::PointCloudColorHandlerCustom<pcl::PointXYZ> point_cloud_color_handler (point_cloud, 150, 150, 150);
	// viewer.addPointCloud (point_cloud, point_cloud_color_handler, "original point cloud");

	viewer.initCameraParameters ();
	setViewerPose(&viewer, range_image->getTransformationToWorldSystem ());

	// Show range image
	pcl::visualization::RangeImageVisualizer range_image_widget (title);
	range_image_widget.showRangeImage (*range_image);

	// Viewer update loop
	while (!viewer.wasStopped ()) {
		range_image_widget.spinOnce ();
		viewer.spinOnce ();
		pcl_sleep (0.01);
		if (!params.live_update) {
			continue;
		}
		Eigen::Affine3f scene_sensor_pose = viewer.getViewerPose();
		range_image->createFromPointCloud (*point_cloud, params.angular_resolution_x, params.angular_resolution_y,
		                                   pcl::deg2rad (360.0f), pcl::deg2rad (180.0f),
		                                   scene_sensor_pose, pcl::RangeImage::LASER_FRAME, kNoiseLevel, kMinRange, kBorderSize);
		range_image_widget.showRangeImage (*range_image);
	}
}
}  // namespace

int main (int argc, char** argv)
{
	std::string program_name = std::string(argv[0]);

	// Parse command-line arguments
	ImageParams params;
	if (pcl::console::find_argument (argc, argv, "-h") >= 0) {
		printUsage (program_name);
		return 0;
	}
	if (pcl::console::find_argument (argc, argv, "-l") >= 0) {
		params.live_update = true;
	}
	pcl::console::parse (argc, argv, "-rx", params.angular_resolution_x);
	pcl::console::parse (argc, argv, "-ry", params.angular_resolution_y);
	int tmp_coordinate_frame;
	if (pcl::console::parse (argc, argv, "-c", tmp_coordinate_frame) >= 0) {
		params.coordinate_frame = pcl::RangeImage::CoordinateFrame (tmp_coordinate_frame);
	}
	std::vector<int> pcd_filename_indices = pcl::console::parse_file_extension_argument (argc, argv, "pcd");
	if (pcd_filename_indices.empty()) {
		printUsage (program_name);
		return 0;
	}
	std::string filename = std::string(argv[pcd_filename_indices[0]]);

	auto point_cloud = std::make_unique<pcl::PointCloud<pcl::PointXYZ>>();
	if (pcl::io::loadPCDFile<pcl::PointXYZ> (filename, *point_cloud) == -1) {
		throw std::invalid_argument("Couldn't read file test_pcd.pcd \n");
	}

	std::cout << "Running with parameters " << std::endl
	          << params;

	runVisualization(std::move(point_cloud), params);
	return 0;
}
