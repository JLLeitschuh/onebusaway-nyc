package org.onebusaway.nyc.admin.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.onebusaway.nyc.admin.service.DiffService;
import org.onebusaway.nyc.admin.service.bundle.task.DiffTask;
import org.onebusaway.nyc.util.configuration.ConfigurationServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import difflib.DiffUtils;

@Component
public class DiffServiceImpl implements DiffService {
	Logger _log = LoggerFactory.getLogger(DiffTask.class);
	protected String _diff_log_filename;
	protected int context = 0;
	
	ConfigurationServiceClient configurationServiceClient;
	DiffTransformer diffTransformer = new DefaultDiffTransformer();
	
	private final String FILENAME = "gtfs_stats.csv";

	@Autowired
	public void setConfigurationServiceClient(ConfigurationServiceClient configurationServiceClient) {
		this.configurationServiceClient = configurationServiceClient;
	}
	
	public void setDiffTransformer(DiffTransformer diffTransformer) {
		this.diffTransformer = diffTransformer;
	}

	public List<String> diff(String filename1, String filename2){
		if (!new File(filename1).exists()){
			try {
				filename1 = configurationServiceClient.getItem("admin", "bundleStagingDir") 
					+ File.separator
					+ "prod"
					+ File.separator
					+ "outputs"
					+ File.separator
					+ FILENAME;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		List<String> original = fileToLines(filename1);
		List<String> revised  = fileToLines(filename2);
		_log.info("Called diff " + this.getClass().getName() +" between (" + filename1 + ") and (" + filename2 + ")");
		List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
				filename1, filename2, fileToLines(filename1), DiffUtils.diff(original, revised), context);
		return diffTransformer.transform(unifiedDiff);
	}
	
	private List<String> fileToLines(String filename) {
		List<String> lines = new LinkedList<String>();
		if (filename == null) return lines;
		String line = "";
		try {
			BufferedReader in = new BufferedReader(new FileReader(filename));
			while ((line = in.readLine()) != null) {
				lines.add(line);
			}
		} catch (IOException e) {}
		return lines;
	}
	
	private class DefaultDiffTransformer implements DiffTransformer {
		final int[] COLUMN_WIDTHS = {40, 180, 60, 60, 60, 100, 180, 180, 180, 180};
		final String ADD_PREFIX = "<table class=\"greenListData\">" + getColumnTags() + "<tr>";
		final String ADD_SUFFIX = "</tr></table>";
		final String REMOVE_PREFIX = "<table class=\"redListData\">" + getColumnTags() + "<tr>";
		final String REMOVE_SUFFIX = "</tr></table>";
		final String DESCRIPTOR_PREFIX = "<table class=\"blueListData\"><tr><td colspan=10>";
		final String DESCRIPTOR_SUFFIX = "</td></tr></table>";
		
		@Override
		public List<String> transform(List<String> preTransform) {
			List<String> diffResult = new LinkedList<String>(); 
			if (preTransform.size() < 2){
				return null;
			}
			for(String line: preTransform.subList(2, preTransform.size())){
				if (line.startsWith("+")){
					line = ADD_PREFIX + delimit(line) + ADD_SUFFIX;
				}
				else if (line.startsWith("-")){
					line = REMOVE_PREFIX + delimit(line) + REMOVE_SUFFIX;
				}
				else if(line.startsWith("@")){
					line = DESCRIPTOR_PREFIX + line + DESCRIPTOR_SUFFIX;
				}
				diffResult.add(line);
			}
			return diffResult;
		}
		
		private String delimit(String line) {
			String delimitedLine = "";
			for (String entry : line.split(",")) {
				delimitedLine += "<td>" + entry + "</td>";
			}
			return delimitedLine;
		}
		
		private String getColumnTags() {
			String tag = "";
			for (int width : COLUMN_WIDTHS) {
				tag += "<col style=\"width:" + width + "px\">";
			}
			return tag;
		}
	}
}