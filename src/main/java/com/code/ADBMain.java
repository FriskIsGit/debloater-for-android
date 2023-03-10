package com.code;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class ADBMain{
    private static boolean CHECK_IF_ADB_EXISTS = true;
    public static void main(String[] args) {
        //no arguments, ask for path
        if(args.length == 0){
            System.out.println("Provide path to directory where adb is located:");
            new ADBExecutor(getValidPath()).run();
        }
        //path arg provided
        else if(args.length == 1){
            validatePathAndRun(args[0]);
        }
        else{
            String path = Utilities.stringArrayToString(args);
            validatePathAndRun(path);
        }
    }
    private static void validatePathAndRun(String path){
        boolean isValid = isValidPath(path);
        if(isValid){
            new ADBExecutor(path).run();
        }
        else{
            System.out.println("Provided path is not a directory, provide a valid directory:");
            new ADBExecutor(getValidPath()).run();
        }
    }
    private static String getValidPath(){
        Scanner scan = new Scanner(System.in);
        String line;
        while(true){
            line = scan.nextLine();
            if(isValidPath(line)){
                break;
            }
        }
        return line;
    }

    private static boolean isValidPath(String line){
        if(line.isEmpty()){
            return false;
        }
        line = Utilities.normalizeStringPath(line);
        File dir = new File(line);
        if(!dir.exists()){
            System.out.println("Provided path is invalid, provide a valid directory:");
            return false;
        }
        Path p = dir.toPath();
        System.out.println(p);
        if(!Files.isDirectory(p)){
            System.out.println("Provided path is not a directory, provide a valid directory:");
            return false;
        }
        if(CHECK_IF_ADB_EXISTS){
            if(hasAdb(p)){
                System.out.println("Adb found, proceeding..");
                return true;
            }else{
                System.out.println("Adb couldn't be located in given directory");
                return false;
            }
        }
        return true;
    }

    private static boolean hasAdb(Path dirPath){
        File[] entries = dirPath.toFile().listFiles();
        for(File f : entries){
            if(f.isFile() && f.getName().startsWith("adb")){
                return true;
            }
        }
        return false;
    }
}
