package Sentiment_Analysis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.json.simple.*;
import org.json.simple.parser.*;
public class Tweet extends Configured implements Tool {
public static class Map extends Mapper<LongWritable, Text, Text, Text>{
private URI[] files;
private HashMap<String,String> AFINN_map = new HashMap<String,String>();
@Override
public void setup(Context context) throws IOException
{
files = DistributedCache.getCacheFiles(context.getConfiguration());
System.out.println("files:"+ files);
Path path = new Path(files[0]);
FileSystem fs = FileSystem.get(context.getConfiguration());
FSDataInputStream in = fs.open(path);
BufferedReader br = new BufferedReader(new InputStreamReader(in));
String line="";
while((line = br.readLine())!=null)
{
String splits[] = line.split("\t");
AFINN_map.put(splits[0], splits[1]);
}
br.close();
in.close();
}
public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException{
String name;
String twt;
String line = value.toString();
String[] tuple = line.split("\\n");
JSONParser jsonParser = new JSONParser();
try{
for(int i=0;i<tuple.length; i++){
JSONObject obj =(JSONObject) jsonParser.parse(line);
String tweet_id = (String) obj.get("id_str");
String tweet_text=(String) obj.get("text");
twt=(String) obj.get("text");
String[] splits = twt.toString().split(" ");
int sentiment_sum=0;
for(String word:splits){
if(AFINN_map.containsKey(word))
{
Integer x=new Integer(AFINN_map.get(word));
sentiment_sum+=x;
}
}
if(sentiment_sum==0)
context.write(new Text(tweet_id),new Text(tweet_text+"::	" + "neutral sentiment"));
else if (sentiment_sum>0)
context.write(new Text(tweet_id),new Text(tweet_text+"::	"+"positive sentiment"));
else
context.write(new Text(tweet_id),new Text(tweet_text+"::	"+"negative sentiment"));	
}
}catch(Exception e){
e.printStackTrace();
}
}
}
public static class Reduce extends Reducer<Text,Text,Text,Text>{
public void reduce(Text key, Text value, Context context) throws IOException, InterruptedException{
context.write(key,value);
}
}
public static void main(String[] args) throws Exception
{
ToolRunner.run(new Tweet(),args);
}
@Override
public int run(String[] args) throws Exception {
// TODO Auto-generated method stub
Configuration conf = new Configuration();
if (args.length != 2) {
System.err.println("Usage: Parse <in> <out>");
System.exit(2);
}
DistributedCache.addCacheFile(new URI("/AFINN.txt"),conf);
Job job = new Job(conf, "Tweet");
job.setJarByClass(Tweet.class);
job.setMapperClass(Map.class);
job.setReducerClass(Reduce.class);
job.setMapOutputKeyClass(Text.class);
job.setMapOutputValueClass(Text.class);
job.setOutputKeyClass(NullWritable.class);
job.setOutputValueClass(Text.class);
job.setInputFormatClass(TextInputFormat.class);
job.setOutputFormatClass(TextOutputFormat.class);
FileInputFormat.addInputPath(job, new Path(args[0]));
FileOutputFormat.setOutputPath(job, new Path(args[1]));
System.exit(job.waitForCompletion(true) ? 0 : 1);
return 0;
}
}