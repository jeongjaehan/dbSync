package com.kth.job;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.log4j.Logger;

public class JobMain {
	private String ktis_db_path = ""; 
	private String ktis_backup_path = ""; 
	private String mybatis_path = ""; // mybatis 설정 class path 
	
	private Logger log = Logger.getLogger(this.getClass());
	
	
	public JobMain() {
		if(System.getProperty("os.name").toUpperCase().startsWith("WINDOW")){ // 테스트
			this.ktis_db_path = "D:/test/"; 	
			this.mybatis_path = "com/kth/job/MybatisConfig-test.xml"; 
		}else{	// 상용
			this.ktis_db_path = "/DATA/ktis/"; 
			this.mybatis_path = "com/kth/job/MybatisConfig.xml"; 
		}
		
		this.ktis_backup_path = this.ktis_db_path+"backup/";
	}

	public SqlSession getSessionFactory(){
		SqlSession session = null;

		try {
			Reader reader = Resources.getResourceAsReader(this.mybatis_path);
			SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
			session = sqlSessionFactory.openSession();
			return session;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return session;
	}


	public int executeTask(){

		File ktisFiles = readKtisDBFiles();
		pushJOB(ktisFiles);
		LinkedList<HashMap> jobList = popJOBList();
		syncKtisDB(jobList);

		return 1;
	}

	/**
	 * ktisDB파일 반환
	 */
	public File readKtisDBFiles(){
		File file = new File(ktis_db_path);
		return file;
	}

	/**
	 * 작업큐에 작업 푸쉬
	 */
	public void pushJOB(File file){
		String[] fileList = file.list();

		SqlSession session = getSessionFactory();
		JobMapper jobMapper = session.getMapper(JobMapper.class);

		for (String fileName : fileList) {
			
			// ktis 파일 체크 subfix && prefix check
			boolean checkKTISFile = fileName.startsWith("kt_gis_iud") && fileName.endsWith(".txt");
			
			if(checkKTISFile){ // ktis 현행화 파일인것들만 작업목록에 등록한다.
				HashMap<String,String> params = new HashMap<String, String>();
				String absolutePath = ktis_db_path+fileName;
				params.put("file_name", absolutePath);

				int count = jobMapper.selectJobQueueCount(params);

				if(count==0){ // 같은 파일이름으로 등록된 잡이 없을경우만
					jobMapper.insertJobQueue(params); //db에 job 등록
					log.info("["+absolutePath+"] ktis 잡등록");
				}
			}
		}

		session.commit();
		session.close();

	}

	/**
	 * 작업큐에 작업 팝
	 * 미작업된 job만 가져옴 
	 */
	public LinkedList<HashMap> popJOBList(){
		SqlSession session = getSessionFactory();
		JobMapper jobMapper = session.getMapper(JobMapper.class);

		LinkedList<HashMap> jobList = jobMapper.selectJobQueueList(null);

		log.debug("현행화 되지않은 ktis DB파일 갯수 : "+jobList.size() +", ["+jobList.toString()+"]");

		session.commit();
		session.close();
		return jobList;
	}
	

	/**
	 * 파일에서 한라인씩 읽어 ktis DB현행화
	 */
	public void syncKtisDB(LinkedList<HashMap> jobList){

		SqlSession session = getSessionFactory();
		JobMapper jobMapper = session.getMapper(JobMapper.class);

		for (HashMap map : jobList) {
			int seq = (Integer)map.get("seq");
			String file_name = (String)map.get("file_name");
			
			long startTime = System.currentTimeMillis();
			log.info("["+file_name+"] 현행화 작업시작");

			try {
				BufferedReader br = new BufferedReader(new FileReader(file_name));
				String entities;
				while ((entities = br.readLine()) != null) {

					log.debug("file : "+file_name+", line : "+entities);
					String attrs[] = entities.split("\\|");	// 구분자 |

					String iud = attrs[0].trim();
					String sysdate = attrs[1].trim();
					String pubname = attrs[2].trim();
					String part1 = attrs[3].trim();
					String part2 = attrs[4].trim();
					String part3 = attrs[5].trim();
					String part4 = attrs[6].trim();
					String addr_detail = attrs[7].trim();
					String b_code = attrs[8].trim();
					String b_name = attrs[9].trim();
					String addr_type = attrs[10].trim();
					String bunji = attrs[11].trim();
					String ho = attrs[12].trim();
					String tel = attrs[13].trim()+"-"+attrs[14].trim(); // 전화번호 합치기 
					String yp = attrs[15].trim();
					String sese_name = attrs[16].trim();
					String calllink_gubun1 = attrs[17].trim();
					String calllink_gubun2 = attrs[18].trim();

					HashMap<String,String> params = new HashMap<String,String>();
					params.put("iud", iud);
					params.put("sysdate", sysdate);
					params.put("pubname", pubname);
					params.put("part1", part1);
					params.put("part2", part2);
					params.put("part3", part3);
					params.put("part4", part4);
					params.put("addr_detail", addr_detail);
					params.put("b_code", b_code);
					params.put("b_name", b_name);
					params.put("addr_type", addr_type);
					params.put("bunji", bunji);
					params.put("ho", ho);
					params.put("tel", tel);
					params.put("yp", yp);
					params.put("sese_name", sese_name);
					params.put("calllink_gubun1", calllink_gubun1);
					params.put("calllink_gubun2", calllink_gubun2);


					int count = jobMapper.selectKTISCount(params); // 전화번호로 조회하여 등록유무 판단
					
					
					if(count == 0){
						jobMapper.insertKTIS(params);
						log.debug("등록 ["+params+"]");
					}else
						jobMapper.updateKTIS(params);
						log.debug("수정 ["+params+"]");
					}
				
				br.close();
			} catch (Exception e) {
				e.printStackTrace();
			}finally{
				completeJOB(map);	//  작업완료시 잡의 상태를 완료상태로 변경 or 파일 백업 디렉토리로 이동 

				session.commit();
//				session.close();
				
				long endTime = System.currentTimeMillis();
				
				log.info("["+file_name+"] 현행화 작업 종료 ("+(endTime - startTime)+" ms)");

			}

		}
	}


	/**
	 * 작업완료시 잡의 상태를 완료상태로 변경 or 파일 백업 디렉토리로 이동 
	 */
	public void completeJOB(HashMap map){
		int seq = (Integer)map.get("seq");
		String file_name = (String)map.get("file_name");


		SqlSession session = getSessionFactory();
		JobMapper jobMapper = session.getMapper(JobMapper.class);

		HashMap<String,Object> params = new HashMap<String,Object>();
		params.put("seq", seq);
		jobMapper.updateJobQueue(params); // 작업 완료 상태 업데이트

		log.info("Job seq ["+seq+"] -> 완료 상태 업데이트");

		session.commit();
		session.close();

		// backup 디렉토리로 파일 이동
		String back_file_name = "backup-"+file_name.substring(file_name.lastIndexOf("/")+1);
		FileUtils.fileMove(file_name, ktis_backup_path+back_file_name);

		log.info("["+file_name+"] 백업 디렉토리로 이동");
	}

	public static void main(String[] args)throws Exception {
		JobMain job = new JobMain();
		job.executeTask();
	}
}
