package org.aptparser;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Main {
    static HashMap<String, AptPackage> PACKAGES = new HashMap<String, AptPackage>();
    static HashMap<String, AptPackage> PROVIDED_PACKAGES = new HashMap<String,AptPackage>();

    public static void main(String[] args)  throws Throwable {
        File[] files = new File("/var/lib/apt/lists/").listFiles();
        String release = "mantic";
        String[] parseSuites = new String[]{"main", "universe"};
        for (var file : files) {
            try {
                if (!acceptFile(file, parseSuites))
                    continue;

                if (!file.getName().contains(release))
                    continue;
                parseFile(file);
            }
            catch (FileNotFoundException t){
                continue;
            }
        }
        for (var p : PACKAGES.values()) {
            if (p._name.contains("wget"))
                System.out.println();
        }
        linkProvides(PACKAGES);
        linkSourcePackages(PACKAGES);
        linkDepends(PACKAGES);

        HashMap<String, AptPackage> java = new HashMap<String, AptPackage>();
        AptPackage jreHeadless = PACKAGES.get("default-jre-headless amd64");
        HashSet<AptPackage> javaEcosystem = new HashSet<AptPackage>();
        fillRdeps(javaEcosystem, jreHeadless);
        for (var p : jreHeadless._deps) {
            fillRdeps(javaEcosystem, p);
        }
        FileOutputStream fos = new FileOutputStream("result.csv");
        for (var p : javaEcosystem) {
            fos.write((p._name + "," + p._tags.get("Section")+ "\n").getBytes("UTF-8"));
        }
        System.out.println("Finished");
    }

    private static void fillRdeps(HashSet<AptPackage> javaEcosystem, AptPackage p) {
        if (p._source_package == null) {
            if (javaEcosystem.contains(p)) {
                return;
            }
            javaEcosystem.add(p);
        } else {
            javaEcosystem.add(p._source_package);
        }

        for (var rdep : p._reverse_deps) {
            if (javaEcosystem.contains(rdep._source_package))
                continue;
            fillRdeps(javaEcosystem, rdep);
        }
    }

    private static void linkProvides(HashMap<String, AptPackage> db) {
        for (var p : db.values()) {
            if (p._isSource)
                continue;
            String provides = p._tags.get("Provides");
            if (provides == null)
                continue;
            String arch = p._tags.get("Architecture");
            for (var name : provides.split(",")) {
                name = name.split("\\(")[0];
                String key = name.trim() + arch;
                PROVIDED_PACKAGES.put(key, p);
            }
        }
        db.putAll(PROVIDED_PACKAGES);
    }

    private static boolean acceptFile(File file, String[] parseSuites) {
        for (var suite : parseSuites) {
            if (file.getName().contains(suite)) {
                if (file.getName().contains("i18n"))
                    return false;
                return true;
            }

        }
        return false;
    }

    private static AptPackage lookupPackage(HashMap<String, AptPackage> db, String name) {
        final String[] supportedArches = new String[] { " all", " amd64", " i386", " ppc64el", " arm64", " armhf"};
        for (var arch : supportedArches) {
            AptPackage p = db.get(name + arch);
            if (p != null)
                return p;
        }
        return null;
    }

    private static void linkDepends(HashMap<String, AptPackage> db) {
        for (var p : db.values()) {
            String arch = p._tags.get("Architecture");
            ArrayList<String> dependFields = new ArrayList<String>();
            for (var field : new String[] { "Depends", "Build-Depends", "Build-Depends-Indep", "Build-Depends-Arch"})
            {
                String depends = p._tags.get(field);
                if (depends != null)
                    dependFields.add(depends);
            }

            for (var depends : dependFields) {
                for (var str : depends.split(",")) {
                    AptPackage dep = null;
                    for (var alt : str.split("\\|")) {
                        alt = alt.split("\\(")[0].trim();
                        alt = alt.split("\\[")[0].trim();
                        alt = alt.split("\\<")[0].trim();
                        alt = alt.split("\\:")[0].trim();
                        dep = lookupPackage(db, alt);
                        if (dep != null)
                            break;
                    }

                    if (dep == null) {
                        for (var name : db.keySet()) {
                            if (name.contains(str))
                                System.out.println();
                        }
                        System.out.println("Unable to resolve Depends " + str);
                    } else {
                        p._deps.add(dep);
                        dep._reverse_deps.add(p);
                    }
                }
            }
        }
    }

    private static void linkSourcePackages(HashMap<String, AptPackage> db) {
        for (var p : db.values()) {
            if (p._isSource)
                continue;
            String source = p._tags.get("Source");
            if (source == null) {
                source = "src:" + p._name;
            } else {
                source = "src:" + source.split("\\(")[0].trim();
            }

            AptPackage src = db.get(source);
            if (src == null) {
                System.out.println("can not get source package "+ source);
            }

            p._source_package = src;
        }
    }


    private static void parseFile(File f) throws Throwable {
        boolean isSource = false;
        if (f.getName().endsWith("Sources"))
            isSource = true;
        try (BufferedReader rd = new BufferedReader( new FileReader(f))) {
            parseReader(rd, isSource, f.getName());
        }
    }

    public static void parseReader(BufferedReader rd, boolean isSource, String name)  throws Exception{
        String line;
        AptPackage p = new AptPackage(isSource);
        p._tags.put("_sourceFile", name);
        String tag = "";
        String value = "";
        while ( (line = rd.readLine()) != null) {
            if ("".equals(line)) {
                commitAptPackage(p);
                p = new AptPackage(isSource);
                p._tags.put("_sourceFile", name);
                tag = "";
                value = "";
            } else {
                if (line.startsWith(" ") || line.startsWith("\t"))
                    value += "\n" + line;
                else {
                    String[] split = line.split(":");
                    if (split.length >= 2) {
                        if (!tag.isEmpty())
                            commitTag(p, tag, value);

                        tag = split[0];
                        value = line.substring(tag.length() +1);
                    }
                }
            }
        }
        if (p._name != null && !p._name.isEmpty()) {
            if (!tag.isEmpty())
                commitTag(p, tag, value);
            commitAptPackage(p);
            p._tags.put("_sourceFile", name);
        }
    }

    private static void commitTag(AptPackage p, String tag, String value) {
        if ("Version".equals(tag)) {
            p._version = value.trim();
        }

        if ("Package".equals(tag)) {
            p._name = (p._isSource ? "src:" : "") + value.trim();
        }

        p._tags.put(tag, value);
    }


    private static void commitAptPackage(AptPackage p){
        if (p._name == null)
            return;
        String key = p._name;
        if (!p._isSource)
            key += p._tags.get("Architecture");
        AptPackage existing = PACKAGES.get(key);
        if (existing == null) {
            PACKAGES.put(key, p);
        } else if (existing._version.compareTo(p._version) < 0 ){
            PACKAGES.put(key, p);
        }
    }
}