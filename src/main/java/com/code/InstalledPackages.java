package com.code;

import java.util.*;

class InstalledPackages{
    private static final int PACKAGE_NAME_OFFSET = 8;
    private final Set<String> uninstallablePackages;
    private final Set<String> allPackages;

    InstalledPackages(String output, List<String> packagesToUninstall){
        int outputLen = output.length();
        allPackages = new HashSet<>(128);

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
            allPackages.add(packageName);
            index = nextNewline + 1;
        }
        System.out.println("Installed packages on phone: " + allPackages.size());
        System.out.println(allPackagesByGroup());
        uninstallablePackages = new HashSet<>();
        for (String name : packagesToUninstall){
            if(allPackages.contains(name)){
                uninstallablePackages.add(name);
            }
        }
    }
    public int uninstallableCount(){
        return uninstallablePackages.size();
    }
    public boolean isUninstallable(String packageName){
        return uninstallablePackages.contains(packageName);
    }
    public Set<String> uninstallableSet(){
        return uninstallablePackages;
    }
    public Set<String> allPackages(){
        return allPackages;
    }
    private String allPackagesByGroup(){
        HashMap<String, List<String>> groupsToPackages = new HashMap<>();
        String groupName = "";
        for (String name : allPackages){
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
