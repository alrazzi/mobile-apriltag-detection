# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.


# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.


# UNCOMMENT ----
#cmake_minimum_required(VERSION 3.4.1)

#add_library( # Specifies the name of the library.
#        apriltag
#
#        # Sets the library as a shared library.
#        SHARED
#
#        # Provides a relative path to your source file(s).
#        src/main/cpp/ApriltagTower.c )
#
## Specifies a path to native header files.
#include_directories(src/main/cpp/include/)
#UNCOMMENT END

# Sets the minimum version of CMake required to build your native library.
cmake_minimum_required(VERSION 3.4.1)

# Specifies the name of your library and its type (SHARED).
add_library(ApriltagTower SHARED src/main/cpp/ApriltagTower.c)

# Include the `apriltag` project as a library
add_subdirectory(src/main/cpp/include)  # Replace with the actual path

# Link your library with the `apriltag` library
target_link_libraries(ApriltagTower apriltag)  # 'apriltag' is the library name from the apriltag project

# Include the necessary header files from apriltag
target_include_directories(ApriltagTower PRIVATE
        "${CMAKE_BINARY_DIR}/src/main/cpp/include"  # Adjust the path
        "${CMAKE_BINARY_DIR}/src/main/cpp/include/common"  # Adjust the path
        )

# Set other options and configurations if needed

# Install your library
install(TARGETS ApriltagTower LIBRARY DESTINATION lib)
