package com.code;

import java.util.*;

class InstalledPackages{
    private static final int PACKAGE_NAME_OFFSET = 8;
    private final Set<String> bloatedInstalled = new HashSet<>();
    private final Set<String> installed = new HashSet<>(128);

    InstalledPackages(String output){
        int outputLen = output.length();
        int index = 0, nextNewline;
        String packageName;
        while(index < outputLen){
            nextNewline = output.indexOf('\n', index + PACKAGE_NAME_OFFSET);
            int decrement = nextNewline;
            //fix made to correctly parse package name, regardless of the number of carriage returns
            while(output.charAt(decrement-1) == '\r'){
                decrement--;
            }
            packageName = output.substring(index + PACKAGE_NAME_OFFSET, decrement);
            installed.add(packageName);
            index = nextNewline + 1;
        }
        System.out.println("Installed packages on phone: " + installed.size());
        System.out.println(allPackagesByGroup());
    }
    public void resolveBloated(List<String> allBloatedPackages){
        for (String name : allBloatedPackages){
            if(installed.contains(name)){
                bloatedInstalled.add(name);
            }
        }
    }
    public int bloatedCount(){
        return bloatedInstalled.size();
    }
    public boolean isBloated(String packageName){
        return bloatedInstalled.contains(packageName);
    }
    public Set<String> bloatedSet(){
        return bloatedInstalled;
    }
    public boolean isInstalled(String packageName){
        return installed.contains(packageName);
    }
    public Set<String> installedSet(){
        return installed;
    }

    public String allPackagesByGroup(){
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
