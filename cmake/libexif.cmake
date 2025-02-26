
include(ExternalProject)

ExternalProject_Add(libexif_external
        URL ${CMAKE_CURRENT_SOURCE_DIR}/library/libexif-0.6.22
        CMAKE_ARGS
            ${CL_ARGS}
            -DCMAKE_INSTALL_PREFIX=${CMAKE_CURRENT_BINARY_DIR}
            #-DCMAKE_INSTALL_LIBDIR=${CMAKE_LIBRARY_OUTPUT_DIRECTORY} #Test

        #DEPENDS
        #BUILD_ALWAYS 1 ## ETA addition: otherwise lib files might not be installed in the output dir

        # fix for "missing and no known rule to make it": https://stackoverflow.com/a/65803911/15401262
        BUILD_BYPRODUCTS ${CMAKE_BINARY_DIR}/lib/libexif.so

        LOG_CONFIGURE 1
        LOG_BUILD 1
        LOG_INSTALL 1
        LOG_UPDATE 1
        )

ExternalProject_Add_Step(
        libexif_external
        add_cmake_files
        COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_SOURCE_DIR}/cmake/libexif_CMakeLists.cmake ${CMAKE_CURRENT_BINARY_DIR}/libexif_external-prefix/src/libexif_external/CMakeLists.txt
        COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_SOURCE_DIR}/cmake/libexif_config.h.cmake ${CMAKE_CURRENT_BINARY_DIR}/libexif_external-prefix/src/libexif_external/config.h.cmake
        DEPENDEES download
        DEPENDERS update
        LOG 1
)

add_library(libexifLib SHARED IMPORTED)
add_dependencies(libexifLib libexif_external)
set_target_properties(
        # Specifies the target library.
        libexifLib
        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION
        # Provides the path to the library you want to import.
        ${CMAKE_BINARY_DIR}/lib/libexif.so
    )
