package com.kth.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public interface JobMapper {
	public void insertJobQueue(HashMap<String,String> params);
	public void updateJobQueue(HashMap<String,Object> params);
	public LinkedList<HashMap> selectJobQueueList(HashMap<String,String> params);
	public int selectJobQueueCount(HashMap<String,String> params);
	
	
	public void insertKTIS(HashMap<String,String> params);
	public ArrayList<HashMap> selectKTISList(HashMap<String,String> params);
	public int selectKTISCount(HashMap<String,String> params);
	public void updateKTIS(HashMap<String,String> params);
	
}
