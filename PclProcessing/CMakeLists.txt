cmake_minimum_required(VERSION 2.8 FATAL_ERROR)

# Iterative closest point uses std::random_shuffle in a kdtree import
# random_shuffle is deprecated in C++14 and removed in 17
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

project(PCL_TREES)
find_package(PCL 1.3 REQUIRED COMPONENTS common io VISUALIZATION)

include_directories(${PCL_INCLUDE_DIRS})
link_directories(${PCL_LIBRARY_DIRS})
add_definitions(${PCL_DEFINITIONS})


add_executable(pcd_write pcd_write.cpp)
target_link_libraries(pcd_write ${PCL_LIBRARIES})

add_executable (visualize_range_image visualize_range_image.cpp)
target_link_libraries (visualize_range_image ${PCL_LIBRARIES} ${PCL_COMMON_LIBRARIES} ${PCL_IO_LIBRARIES} ${PCL_VISUALIZATION_LIBRARIES})

add_executable (iterative_closest_point iterative_closest_point.cpp)
target_link_libraries (iterative_closest_point ${PCL_LIBRARIES})