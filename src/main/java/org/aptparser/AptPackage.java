package org.aptparser;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AptPackage {
    public final boolean _isSource;
    public List<AptPackage> _deps = new ArrayList<AptPackage>();
    public List<AptPackage> _reverse_deps = new ArrayList<AptPackage>();
    public List<AptPackage> _reverse_src_deps = new ArrayList<AptPackage>();
    public AptPackage _source_package;
    public String _name;
    public String _version;
    public HashMap<String, String> _tags= new HashMap<String, String>();

    public AptPackage(boolean isSource) {
        _isSource = isSource;
    }
}
