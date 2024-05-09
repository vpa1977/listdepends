package org.aptparser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;

public class TestParseFile {

    @Test
    public void testTranslation() throws Exception {
        String snippet = """
Package: libpackage-stash-perl
Description-md5: 2cc7309d7c2c8bbf04d343339450b60d
Description-en: module providing routines for manipulating stashes
 Package::Stash is a Perl module that provides an interface for manipulating
 stashes (Perl's symbol tables). These operations are occasionally necessary,
 but often very messy and easy to get wrong.

Package: libpackage-stash-perl2
Description-md5: 2cc7309d7c2c8bbf04d343339450b60d
Description-en: module providing routines for manipulating stashes
 Package::Stash is a Perl module that provides an interface for manipulating
 stashes (Perl's symbol tables). These operations are occasionally necessary,
 but often very messy and easy to get wrong.
                """;
        try (BufferedReader rd = new BufferedReader(new StringReader(snippet))) {
            Main.parseReader(rd, false);;
            AptPackage p = Main.PACKAGES.get("libpackage-stash-perl");
            Assertions.assertNotNull(p);
            AptPackage p2 = Main.PACKAGES.get("libpackage-stash-perl2");
            Assertions.assertNotNull(p2);
        }
    }
}
