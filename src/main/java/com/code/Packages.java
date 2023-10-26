package com.code;

import java.util.*;

public class Packages{
    private static final int PACKAGE_NAME_OFFSET = 8;
    private Set<String> installed = new HashSet<>(128);
    private Set<String> bloatedInstalled = new HashSet<>();

    private Packages(){
    }

    //parsers package names, expected form:
    //"package:com.group.example\n"
    public static Packages parse(String output){
        Packages packages = new Packages();
        int outputLen = output.length();
        int index = 0, nextNewline;
        String packageName;
        while(index < outputLen-1){
            nextNewline = output.indexOf('\n', index + PACKAGE_NAME_OFFSET);
            int decrement = nextNewline;
            //fix made to correctly parse package name, regardless of the number of carriage returns
            while(output.charAt(decrement-1) == '\r'){
                decrement--;
            }
            packageName = output.substring(index + PACKAGE_NAME_OFFSET, decrement);
            packages.installed.add(packageName);
            index = nextNewline + 1;
        }
        System.out.println("Installed packages on phone: " + packages.installed.size());
        System.out.println(packages.sortedByGroups());
        return packages;
    }

    public static Packages from(List<String> packages){
        Packages packs = new Packages();
        packs.installed = new HashSet<>(packages);
        return packs;
    }

    public void resolveExisting(List<String> allBloatedPackages){
        for (String name : allBloatedPackages){
            if(installed.contains(name)){
                bloatedInstalled.add(name);
            }
        }
    }
    public int bloatedCount(){
        return bloatedInstalled.size();
    }
    public Set<String> getInstalledBloated(){
        return bloatedInstalled;
    }
    public boolean isBloated(String packageName){
        return bloatedInstalled.contains(packageName);
    }
    public boolean isInstalled(String packageName){
        return installed.contains(packageName);
    }

    public String sortedByGroups(){
        HashMap<String, List<String>> groupsToPackages = new HashMap<>();
        String groupName = "";
        for (String name : installed){
            int firstIndex = name.indexOf('.');
            if(firstIndex == -1){
                resolveInMap(groupsToPackages, name, name);
                continue;
            }
            firstIndex++;
            int secondDot = name.indexOf('.', firstIndex);
            if(secondDot == -1){
                groupName = name.substring(firstIndex);
            }else{
                groupName = name.substring(firstIndex, secondDot);
            }
            resolveInMap(groupsToPackages, groupName, name);
        }
        StringBuilder strBuilder = new StringBuilder(256);
        groupsToPackages.forEach((gName,list) -> {
            strBuilder.append('[').append(gName).append(']').append('\n');
            for(String pckg : list){
                strBuilder.append(pckg).append('\n');
            }
        });
        strBuilder.append("------------------------------");
        return strBuilder.toString();
    }

    private void resolveInMap(HashMap<String, List<String>> groupToPackages, String groupName, String fullName){
        if(groupToPackages.containsKey(groupName)){
            groupToPackages.get(groupName).add(fullName);
        }else{
            groupToPackages.put(groupName, new ArrayList<>(Arrays.asList(fullName)));
        }
    }

}
