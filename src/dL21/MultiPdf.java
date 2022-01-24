package dL21;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

import dL21.TopicModel;
import dL21.TopicModel.DcPdfAutopsy;

public class MultiPdf {

	
    public static void main(String args[]) throws Exception
    {
    	
    	//This path points to the folder where all the pdfs are stored
        final File folder = new File("pdfs/");
    	
    	TopicModel tm = new TopicModel();

    	//Container for data returned from analyzing all pdfs
    	ArrayList<DcPdfAutopsy> all_pdfs = new ArrayList<DcPdfAutopsy>();
    
        for (final File fileEntry : folder.listFiles()) {        
          //System.out.println(fileEntry.getName());              
          DcPdfAutopsy report = tm.new DcPdfAutopsy();          
          report = tm.returnAllExtractedPdfData(folder + "/" + fileEntry.getName());
          all_pdfs.add(report);           
        }
    	
        //Just for testing
        System.out.println("reporting: " + all_pdfs.get(0).extracted_data.total_nr_words);
        System.out.println("reporting: " + all_pdfs.get(0).extracted_topics.get(0).keyword.get(0)); //first keyword for first topic
        System.out.println("reporting: " + all_pdfs.get(0).extracted_topics.get(0).weight.get(0));  //weight for first keyword for first topic
        System.out.println("reporting: " + all_pdfs.get(0).extracted_topics.get(0).distribution); //distribution (score, the higher the better) of second topic

        System.out.println("reporting: " + all_pdfs.get(0).extracted_topics.get(1).keyword.get(0)); //first keyword for second topic
        System.out.println("reporting: " + all_pdfs.get(0).extracted_topics.get(1).weight.get(0));  //weight for first keyword for second topic
        System.out.println("reporting: " + all_pdfs.get(0).extracted_topics.get(1).distribution); //distribution (score, the higher the better) of second topic
        
        System.out.println("reporting: " + all_pdfs.get(0).extracted_images.get(0).image);	//BufferedImage Object of first image
        System.out.println("reporting: " + all_pdfs.get(0).extracted_images.get(0).rank);  //*new* rank of image
        System.out.println("reporting: " + all_pdfs.get(0).extracted_images.get(0).found_on_page_nr); //pdf page where image was found
        
        System.out.println("reporting: " + all_pdfs.get(0).extracted_data.author); //author
        System.out.println("reporting: " + all_pdfs.get(0).extracted_data.total_nr_pages); //total number of pages
        System.out.println("reporting: " + all_pdfs.get(0).extracted_data.total_nr_graphic_objects); //total nr of graphic elements
        System.out.println("reporting: " + all_pdfs.get(0).extracted_data.total_nr_words); //total nr of words

        
        System.out.println("reporting: " + all_pdfs.get(1).extracted_data.total_nr_words);
        System.out.println("reporting: " + all_pdfs.get(2).extracted_data.total_nr_words);
        System.out.println("reporting: " + all_pdfs.get(3).extracted_data.total_nr_words);
        
    
    }
    
    
}

