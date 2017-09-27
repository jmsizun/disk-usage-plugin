package hudson.plugins.disk_usage;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.scheduler.CronTab;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;


/**
 * A Thread responsible for gathering disk usage information
 * 
 * @author dvrzalik
 */
@Extension
public class BuildDiskUsageCalculationThread extends DiskUsageCalculation {   
    
    //last scheduled task;
    private static DiskUsageCalculation currentTask;
      
    public BuildDiskUsageCalculationThread(){        
        super("Calculation of builds disk usage"); 
    }   
    
    @Override
    public void execute(TaskListener listener) throws IOException, InterruptedException {
        System.err.println("start build usage " + !isCancelled() + startExecution());
        if(!isCancelled() && startExecution()){
            try{
                List<Item> items = new ArrayList<Item>();
                ItemGroup<? extends Item> itemGroup = Jenkins.getInstance();
                items.addAll(DiskUsageUtil.getAllProjects(itemGroup));
                System.err.println("calculation for porjects");
                for (Object item : items) {
                    if (item instanceof AbstractProject) {
                        AbstractProject project = (AbstractProject) item;
                      //  if (!project.isBuilding()) {
                            DiskUsageProperty property = DiskUsageUtil.getDiskUsageProperty(project);
                            ProjectDiskUsage diskUsage = property.getProjectDiskUsage();
                            for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(true)){ 
                                Map<Integer,AbstractBuild> loadedBuilds = project._getRuns().getLoadedBuilds();
                                AbstractBuild build = loadedBuilds.get(information.getNumber());
                                //do not calculat builds in progress
                                if(build!=null && build.isBuilding()){
                                    continue;
                                }
                                try{
                                    System.err.println("calculate fo r " + project + " " + information.getId());
                                    DiskUsageUtil.calculateDiskUsageForBuild(information.getId(), project);
                                }
                                catch(Exception e){
                                    logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), e);
                                }
                            }
                       // } 
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error when recording disk usage for builds", ex);
            }
            DiskUsageJenkinsAction.getInstance().actualizeCashedBuildsData();
        }
        else{
            DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
            if(plugin.getConfiguration().isCalculationBuildsEnabled()){
                logger.log(Level.FINER, "Calculation of builds is already in progress.");
            }
            else{
                logger.log(Level.FINER, "Calculation of builds is disabled.");
            }
        }
    }
    
    public CronTab getCronTab() throws ANTLRException{
        String cron = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class).getConfiguration().getCountIntervalForBuilds();
        CronTab tab = new CronTab(cron);
        return tab;
    }   

    @Override
    public AperiodicWork getNewInstance() {   
        if(currentTask!=null){
            currentTask.cancel();
        }
        else{
            cancel();
        }
        currentTask = new BuildDiskUsageCalculationThread();
        return currentTask;
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentTask;
    }
    
    private boolean startExecution(){
        DiskUsagePlugin plugin = Jenkins.getInstance().getPlugin(DiskUsagePlugin.class);
        if(!plugin.getConfiguration().isCalculationBuildsEnabled()) {
            System.err.println("is dissabled");
            return false;
        }
        return !isExecutingMoreThenOneTimes();
    }
    
}
