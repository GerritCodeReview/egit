package org.eclipse.egit.core.internal.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.core.test.GitTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BlobStorageTest extends GitTestCase {

	Repository repository;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		repository = new Repository(gitDir);
		repository.create();
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();
		rmrf(repository.getWorkDir());
	}

	@Test
	public void testOk() throws Exception {
		ObjectId id = createFile(repository, project.getProject(), "file", "data");
		BlobStorage blobStorage = new BlobStorage(repository, "p/file", id);
		assertEquals("file", blobStorage.getName());
		assertEquals("data", slurpAndClose(blobStorage.getContents()));
		assertEquals(Path.fromPortableString("p/file").toOSString(), blobStorage.getFullPath().toOSString());

	}

	@Test
	public void testFailNotFound() throws Exception {
		BlobStorage blobStorage = new BlobStorage(repository, "file", ObjectId.fromString("0123456789012345678901234567890123456789"));
		assertEquals("file", blobStorage.getName());
		try {
			blobStorage.getContents();
			fail("We should not be able to read this 'blob'");
		} catch (CoreException e) {
			assertEquals("Git blob 0123456789012345678901234567890123456789 with path file not found", e.getMessage());
		}
	}

	@Test
	public void testFailWrongType() throws Exception {
		createEmptyTree(repository);
		BlobStorage blobStorage = new BlobStorage(repository, "file", ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904"));
		assertEquals("file", blobStorage.getName());
		try {
			blobStorage.getContents();
			fail("We should not be able to read this blob");
		} catch (CoreException e) {
			assertEquals("IO error reading Git blob 4b825dc642cb6eb9a060e54bf8d69288fbee4904 with path file", e.getMessage());
		}
	}

	@Test
	public void testFailCorrupt() throws Exception {
		try {
			createFileCorruptShort(repository, project.getProject(), "file", "data");
			createEmptyTree(repository);
			BlobStorage blobStorage = new BlobStorage(repository, "file", ObjectId.fromString("6320cd248dd8aeaab759d5871f8781b5c0505172"));
			assertEquals("file", blobStorage.getName());
			blobStorage.getContents();
			fail("We should not be able to read this blob");
		} catch (CoreException e) {
			assertEquals("IO error reading Git blob 6320cd248dd8aeaab759d5871f8781b5c0505172 with path file", e.getMessage());
		} catch (NoSuchMethodException e) {
			System.err.println("testFailCorrupt requires java 1.6: ignored");
		}
	}

	@Test
	public void testFailCorrupt2() throws Exception {
		try {
			createFileCorruptShort(repository, project.getProject(), "file", "datjhjhjhjhjhjhjjkujioedfughjuop986rdfghjhiu7867586redtfguy675r6tfguhyo76r7tfa");
			BlobStorage blobStorage = new BlobStorage(repository, "file", ObjectId.fromString("526ef34fc76ab0c35ccee343bda1a626efbd4134"));
			assertEquals("file", blobStorage.getName());
			blobStorage.getContents();
			fail("We should not be able to read this blob");
		} catch (CoreException e) {
			assertEquals("IO error reading Git blob 526ef34fc76ab0c35ccee343bda1a626efbd4134 with path file", e.getMessage());
		} catch (NoSuchMethodException e) {
			System.err.println("testFailCorrupt requires java 1.6: ignored");
		}
	}

	private String slurpAndClose(InputStream inputStream) throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		try {
			int ch;
			while ((ch = inputStream.read()) != -1) {
				stringBuilder.append((char)ch);
			}
		} finally {
			inputStream.close();
		}
		return stringBuilder.toString();
	}

}
