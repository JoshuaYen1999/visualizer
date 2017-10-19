package visualizer;

import visualizer.FmowVisualizer.Box;

public class MetaData {
	public double gsd;
	public String country_code;
	public String utm;
	public String timestamp;
	public String img_filename;
	public int img_width;
	public int img_height;
	
	public int cloud_cover;
	public double mean_pixel_height;
	public double mean_pixel_width;
	public double multi_resolution_dbl;
	public double multi_resolution_end_dbl;
	public double multi_resolution_max_dbl;
	public double multi_resolution_min_dbl;
	public double multi_resolution_start_dbl;
	public double off_nadir_angle_dbl;
	public double off_nadir_angle_end_dbl;
	public double off_nadir_angle_max_dbl;
	public double off_nadir_angle_min_dbl;
	public double off_nadir_angle_start_dbl;
	public double pan_resolution_dbl;
	public double pan_resolution_end_dbl;
	public double pan_resolution_max_dbl;
	public double pan_resolution_min_dbl;
	public double pan_resolution_start_dbl;
	public String scan_direction;
	public double sun_azimuth_dbl;
	public double sun_azimuth_max_dbl;
	public double sun_azimuth_min_dbl;
	public double sun_elevation_dbl;
	public double sun_elevation_max_dbl;
	public double sun_elevation_min_dbl;
	public double target_azimuth_dbl;
	public double target_azimuth_end_dbl;
	public double target_azimuth_max_dbl;
	public double target_azimuth_min_dbl;
	public double target_azimuth_start_dbl;
	
	public int[] approximate_wavelengths;
	public Box[] bounding_boxes;
}