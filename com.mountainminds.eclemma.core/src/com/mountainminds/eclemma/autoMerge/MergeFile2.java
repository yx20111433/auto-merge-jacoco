package com.mountainminds.eclemma.autoMerge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.eclipse.compare.internal.DocLineComparator;
import org.eclipse.compare.rangedifferencer.RangeDifference;
import org.eclipse.compare.rangedifferencer.RangeDifferencer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFileState;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.text.Document;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ICoverageNode.CounterEntity;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.internal.analysis.ClassCoverageImpl;
import org.jacoco.core.internal.analysis.LineImpl;
import org.jacoco.core.internal.analysis.MethodCoverageImpl;
import org.jacoco.core.internal.analysis.SourceFileCoverageImpl;

import com.mountainminds.eclemma.core.ICorePreferences;
import com.mountainminds.eclemma.internal.core.EclEmmaCorePlugin;
import com.mountainminds.eclemma.internal.core.analysis.AnalyzedNodes;
import com.mountainminds.eclemma.internal.core.analysis.PackageFragementRootAnalyzer;

/**
 * @author xie
 *
 */
/**
 * @author xie
 * 
 */
@SuppressWarnings({ "restriction", "nls" })
public class MergeFile2 {

  private Properties newJavaFileVersion = new Properties();
  private Properties oldJavaFileVersion = new Properties();

  private String ProjectRootWhitProject;

  private HashMap<String, SourceFileInfo> SourceFiles = new HashMap<String, SourceFileInfo>();
  private HashMap<String, SourceFileInfo> oldfiles = new HashMap<String, SourceFileInfo>();
  private OldFileAnalyzer oldFileAnalyzer;
  private PackageFragementRootAnalyzer rootAnaLyzer;
  private IPackageFragmentRoot Pakcageroot;
  private Map<String, IClassCoverage> ClassCoverageMap;
  private Map<String, ISourceFileCoverage> SourceCoverageMap;
  private String rootPath;

  /**
   * 合并当前测试session，和项目历史测试数据
   * 
   * @param session
   * @param description
   * @param monitor
   * @return
   * @throws CoreException
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public void AutoMergeProjectSessions(PackageFragementRootAnalyzer rootAna,
      IPackageFragmentRoot root) throws CoreException, IOException {
    // DocTest.test();
    // if (DocTest.test())
    // return;
    LauncherInfo.setJavaproject(root.getJavaProject());
    ConsoleMessage.showMessage("AutoMergeProjectSessions :"
        + LauncherInfo.ProjectRoot);
    rootAnaLyzer = rootAna;
    Pakcageroot = root;
    ProjectRootWhitProject = LauncherInfo.getJavaproject().getProject()
        .getLocation().toFile().getAbsolutePath();

    // ConsoleMessage.showMessage("ProjectRoot" + LauncherInfo.ProjectRoot);
    // ConsoleMessage.showMessage("ProjectRootWhitProject"
    // + ProjectRootWhitProject);
    SourceFileInfo sourceFileInfo = new SourceFileInfo();
    SourceFiles = (HashMap<String, SourceFileInfo>) sourceFileInfo
        .loadFromPackageFragementRootAnalyzer(rootAnaLyzer, root);

    rootPath = root.getPath().toString().replace("/", "_");
    // SourceFileInfo.loadAllFile(null, true);
    // 没有历史数据
    if (!ExistProjectMergeSession()) {

      for (SourceFileInfo fileinfo : SourceFiles.values()) {
        newJavaFileVersion.put(fileinfo.getJavafilename(),
            String.valueOf(fileinfo.getJavafile().getLocalTimeStamp()));
        fileinfo.saveToFile();
      }
      saveJavaFileVersion();
      return;
    }
    // load history

    ICorePreferences jacocPrefernce = EclEmmaCorePlugin.getInstance()
        .getPreferences();
    String inculdes = jacocPrefernce.getAgentIncludes();
    String excludes = jacocPrefernce.getAgentExcludes();
    // ConsoleMessage.showMessage("inculdes:" + inculdes + ":" + excludes);
    oldfiles = sourceFileInfo.loadAllFile(root.getPath().toString(), inculdes,
        excludes);

    // sourceFileInfo.getOldFiles();

    readFileVersion();
    newJavaFileVersion = oldJavaFileVersion;
    // 对应本次没有运行，但之前运行的java文件生成SourceFileCoverageImpl
    oldFileAnalyzer = new OldFileAnalyzer(rootAnaLyzer.getExecutiondata(),
        oldfiles);
    oldFileAnalyzer.analyze(root);

    Map<Object, AnalyzedNodes> cache = oldFileAnalyzer.getCache();
    IResource sourceRoot = SourceFileInfo.getClassfilesLocation(Pakcageroot);

    AnalyzedNodes nodes = cache.get(sourceRoot);

    // for (String str : nodes.getSourcemap().keySet()) {
    // ConsoleMessage.showMessage("oldFileAnalyzer Key s" + str);
    // }

    ClassCoverageMap = rootAnaLyzer.getCache()
        .get(SourceFileInfo.getClassfilesLocation(Pakcageroot)).getClassmap();
    // 增加没有运行的文件

    Collection<IClassCoverage> oldclasses = oldFileAnalyzer.getCache()
        .get(SourceFileInfo.getClassfilesLocation(Pakcageroot)).getClassmap()
        .values();
    for (IClassCoverage cls : oldclasses) {
      if (!ClassCoverageMap.containsKey(cls.getName())) {
        ClassCoverageMap.put(cls.getName(), cls);
        System.err.println("put old classes " + cls.getName());
      }
    }
    SourceCoverageMap = rootAnaLyzer.getCache()
        .get(SourceFileInfo.getClassfilesLocation(Pakcageroot)).getSourcemap();

    // 处理旧文件
    for (SourceFileInfo oldfileinfo : oldfiles.values()) {
      // 更加文件情况处理lines
      addOldsourcefile(oldfileinfo);

    }

    /*
     * // 增加没有运行的文件 Collection<ISourceFileCoverage> oldSource =
     * oldFileAnalyzer.getCache()
     * .get(SourceFileInfo.getClassfilesLocation(Pakcageroot)).getSourcemap()
     * .values(); for (ISourceFileCoverage cls : oldSource) { if
     * (!SourceCoverageMap.containsKey(cls.getName())) {
     * SourceCoverageMap.put(cls.getName(), cls); } }
     */

    // sava file version and sourcefile info

    // 处理旧文件,保留文件信息
    for (SourceFileInfo oldfileinfo : SourceFiles.values()) {
      oldfileinfo.saveToFile();
      newJavaFileVersion.put(oldfileinfo.getJavafilename(),
          String.valueOf(oldfileinfo.getJavafile().getLocalTimeStamp()));
    }
    saveJavaFileVersion();
  }

  private void ChangeClassFile(SourceFileCoverageImpl sourceFilesCoverage) {

    // todo ,rootAnaLyzer 没有的情况
    Iterator<IClassCoverage> ClassCoverages = ClassCoverageMap.values()
        .iterator();

    while (ClassCoverages.hasNext()) {
      IClassCoverage coverage = ClassCoverages.next();
      if (coverage instanceof ClassCoverageImpl) {
        ClassCoverageImpl classCoverage = (ClassCoverageImpl) coverage;

        System.err.println("ChangeClassFile getSourceFileName:"
            + classCoverage.getPackageName() + "..."
            + classCoverage.getSourceFileName() + "..."
            + sourceFilesCoverage.getPackageName() + "..."
            + sourceFilesCoverage.getName() + ".." + classCoverage.getName());

        ClassCoverageImpl newClassCoverage = new ClassCoverageImpl(
            classCoverage.getName(), classCoverage.getId(),
            classCoverage.getSignature(), classCoverage.getSuperName(),
            classCoverage.getInterfaceNames());

        if (classCoverage.getPackageName().equals(
            sourceFilesCoverage.getPackageName())
            && classCoverage.getSourceFileName().equals(
                sourceFilesCoverage.getName())) {
          Collection<IMethodCoverage> methodes = classCoverage.getMethods();

          System.err.println(classCoverage.getName() + ".."
              + classCoverage.getCounter(CounterEntity.LINE).getMissedCount()
              + "/"
              + classCoverage.getCounter(CounterEntity.LINE).getCoveredCount());
          for (IMethodCoverage meth : methodes) {
            // ConsoleMessage.showMessage(meth.getName() + ".."
            // + meth.getCounter(CounterEntity.LINE).getMissedCount() + "/"
            // + meth.getCounter(CounterEntity.LINE).getCoveredCount());
            if (meth instanceof MethodCoverageImpl) {
              MethodCoverageImpl methImpl = (MethodCoverageImpl) meth;

              MethodCoverageImpl newMethImpl = new MethodCoverageImpl(
                  methImpl.getName(), methImpl.getDesc(),
                  methImpl.getSignature());

              for (int i = methImpl.getFirstLine(); i <= methImpl.getLastLine(); i++) {
                LineImpl line = sourceFilesCoverage.getLine(i);
                newMethImpl.increment(line.getInstructionCounter(),
                    line.getBranchCounter(), i);

              }
              newClassCoverage.addMethod(newMethImpl);
            }
            newClassCoverage.setSourceFileName(classCoverage
                .getSourceFileName());
            // classCoverage.addMethod(meth);
          }

          methodes = newClassCoverage.getMethods();

          // ConsoleMessage.showMessage(newClassCoverage.getName()
          // + ".."
          // + newClassCoverage.getCounter(CounterEntity.LINE)
          // .getMissedCount()
          // + "/"
          // + newClassCoverage.getCounter(CounterEntity.LINE)
          // .getCoveredCount());
          // for (IMethodCoverage meth : methodes) {
          // ConsoleMessage.showMessage(meth.getName() + ".."
          // + meth.getCounter(CounterEntity.LINE).getMissedCount() + "/"
          // + meth.getCounter(CounterEntity.LINE).getCoveredCount());
          //
          // }
          ClassCoverageMap.put(newClassCoverage.getName(), newClassCoverage);
        }

      }
    }

  }

  private void addOldsourcefile(SourceFileInfo oldfileinfo) throws IOException,
      CoreException {
    String oldversion = oldJavaFileVersion.getProperty(oldfileinfo
        .getJavafilename());
    if (oldversion == null)
      return;

    // int filelinenumber = getFileLine(oldfileinfo.getJavafile());

    SourceFileCoverageImpl sourceFilesCoverage;
    // 先取OLD

    Map<Object, AnalyzedNodes> cache = oldFileAnalyzer.getCache();
    IResource root = SourceFileInfo.getClassfilesLocation(Pakcageroot);

    AnalyzedNodes nodes = cache.get(root);
    sourceFilesCoverage = (SourceFileCoverageImpl) nodes.getSourcemap().get(
        oldfileinfo.getClassName());

    if (sourceFilesCoverage == null) {
      System.err.println("getFirstLine from new...."
          + oldfileinfo.getClassName());
      cache = rootAnaLyzer.getCache();
      root = SourceFileInfo.getClassfilesLocation(Pakcageroot);

      nodes = cache.get(root);
      sourceFilesCoverage = (SourceFileCoverageImpl) nodes.getSourcemap().get(
          oldfileinfo.getClassName());

    }
    System.err.println("getFirstLine...." + oldfileinfo.getClassName()
        + sourceFilesCoverage.getFirstLine());
    LineImpl[] lines = sourceFilesCoverage.getLines();
    if (lines == null) {
      System.err.println("not found...." + oldfileinfo.getClassName());
      // ConsoleMessage.showMessage("not found...." +
      // oldfileinfo.getClassName());

    }
    LineImpl[] oldlines = oldfileinfo.getLines();
    RangeDifference[] diffs = getNoChange(oldfileinfo);

    // 更新覆盖信息
    for (RangeDifference diff : diffs) {
      // ConsoleMessage.showMessage(sourceFilesCoverage.getName() + "diff:"
      // + diff.kind() + ":" + diff.leftStart() + ":" + diff.leftEnd() + ":"
      // + diff.rightStart() + ":" + diff.rightEnd());
      // ConsoleMessage.showMessage("lines:" + lines.length + ":"
      // + oldlines.length);
      // ConsoleMessage.showMessage("lines:" + oldfileinfo.getFristLine() + ":"
      // + oldfileinfo.getLastLine());
      // ConsoleMessage.showMessage("lines:" +
      // sourceFilesCoverage.getFirstLine()
      // + ":" + sourceFilesCoverage.getLastLine());

      if (diff.kind() != RangeDifference.NOCHANGE)
        continue;

      int start = diff.leftStart() + 1;
      int end = diff.leftEnd() + 1;
      int otherstart = diff.rightStart() + 1;

      /*
       * ConsoleMessage.showMessage("change start:" + start + "," + end + "," +
       * otherstart + "," + oldfileinfo.getFristLine() + "," +
       * oldfileinfo.getLastLine() + ',' + sourceFilesCoverage.getFirstLine());
       */
      // int otherend = diff.rightEnd() + 1;
      // 判断是否为有效行
      if (start < oldfileinfo.getFristLine()) {
        otherstart = otherstart + (oldfileinfo.getFristLine() - start);
        start = oldfileinfo.getFristLine();
      }
      if (end > oldfileinfo.getLastLine()) {

        // otherend = otherend - (oldfileinfo.getLastLine() - end);
        end = oldfileinfo.getLastLine();
      }

      if (otherstart < sourceFilesCoverage.getFirstLine()) {
        start = start + (sourceFilesCoverage.getFirstLine() - otherstart);
        otherstart = sourceFilesCoverage.getFirstLine();
      }

      System.err.println("change start:" + start + "," + end + "," + otherstart
          + "," + oldfileinfo.getFristLine() + "," + oldfileinfo.getLastLine()
          + ',' + sourceFilesCoverage.getFirstLine());

      for (int i = start, j = otherstart; i <= end; i++, j++) {
        // ConsoleMessage.showMessage("change line:" + j);
        LineImpl line = lines[j - sourceFilesCoverage.getFirstLine()];
        // 是有效行
        if (line == null) {
          // ConsoleMessage.showMessage("change null line:" + j);
          // ConsoleMessage.showMessage("change  line:" + j);
          lines[j - sourceFilesCoverage.getFirstLine()] = oldlines[i
              - oldfileinfo.getFristLine()];

        } else {
          // ConsoleMessage.showMessage("line status " + j + ":"
          // + line.getInstructionCounter().getStatus());
          if (line.getInstructionCounter().getStatus() == ICounter.NOT_COVERED
              || line.getInstructionCounter().getStatus() == ICounter.PARTLY_COVERED) {
            // ConsoleMessage.showMessage("change status line:" + j);
            lines[j - sourceFilesCoverage.getFirstLine()] = oldlines[i
                - oldfileinfo.getFristLine()];

          }

        }

      }

    }
    sourceFilesCoverage.setLines(lines);

    // 更新分析结果供ui使用
    // 重新计算sourcefile的cover供UI使用
    SourceFileCoverageImpl newsourceFilesCoverage = new SourceFileCoverageImpl(
        sourceFilesCoverage.getName(), sourceFilesCoverage.getPackageName());
    for (int i = sourceFilesCoverage.getFirstLine(); i <= sourceFilesCoverage
        .getLastLine(); i++) {
      newsourceFilesCoverage.increment(sourceFilesCoverage.getLine(i)
          .getInstructionCounter(), sourceFilesCoverage.getLine(i)
          .getBranchCounter(), i);
    }

    SourceCoverageMap.put(oldfileinfo.getClassName(), newsourceFilesCoverage);
    // (SourceFileCoverageImpl) nodes.getSourcemap().get(
    // oldfileinfo.getClassName());
    // 修改classCoverage供UI使用
    ChangeClassFile(sourceFilesCoverage);

    SourceFileInfo temp = oldfileinfo;

    temp.setFristLine(sourceFilesCoverage.getFirstLine());
    temp.setLastLine(sourceFilesCoverage.getLastLine());

    // temp.setJavafile(javafile);
    // 更新timepstamp
    temp.setFiletimestamp(String.valueOf(oldfileinfo.getJavafile()
        .getLocalTimeStamp()));

    temp.setLines(lines);
    // temp.set
    SourceFiles.put(temp.getJavafilename(), temp);
  }

  public int getFileLine(IFile file) throws IOException, CoreException {
    String cur = readString(file.getContents());
    Document d1 = new Document();
    d1.set(cur);
    return d1.getNumberOfLines();
  }

  private RangeDifference[] getNoChange(SourceFileInfo oldfileinfo)
      throws IOException, CoreException {
    String cur = readString(oldfileinfo.getJavafile().getContents());

    String his = oldfileinfo.getOldfileContent();

    Document d1 = new Document();
    d1.set(cur);
    Document d2 = new Document();
    d2.set(his);

    DocLineComparator doc1 = new DocLineComparator(d1, null, false);
    DocLineComparator doc2 = new DocLineComparator(d2, null, false);

    RangeDifference[] differences = RangeDifferencer.findRanges(doc2, doc1);
    return differences;
  }

  public RangeDifference[] getNoChange(IFile file, String timestamp)
      throws IOException, CoreException {

    String cur = readString(file.getContents());
    // String timestamp = oldJavaFileVersion.getProperty(c
    // .getJavafilename());

    // 文件没有修改

    String his = "";
    if (timestamp.equals(String.valueOf(file.getLocalTimeStamp()))) {
      his = cur;
    } else

      his = getFileHistory(file, timestamp);

    Document d1 = new Document();
    d1.set(cur);
    Document d2 = new Document();
    d2.set(his);

    DocLineComparator doc1 = new DocLineComparator(d1, null, false);
    DocLineComparator doc2 = new DocLineComparator(d2, null, false);

    // ConsoleMessage.showMessage("lines\r\n" + cur + "\r\n---end");
    // saveHistForTesting(cur, "cur_" + file.getName(), timestamp);

    // ConsoleMessage.showMessage("lines\r\n" + his + "\r\n--end");
    // saveHistForTesting(his, "his_" + file.getName(), timestamp);

    RangeDifference[] differences = RangeDifferencer.findRanges(doc2, doc1);
    return differences;

  }

  // private void saveHistForTesting(String str, String fileName, String
  // timeStamp)
  // throws IOException {
  // File tempfile = new File("d:/temp/" + fileName + timeStamp);
  // FileOutputStream output = new FileOutputStream(tempfile);
  // output.write(str.getBytes());
  // output.close();
  // }

  private String getFileHistory(IFile file, String timestamp)
      throws CoreException, IOException {
    IFileState[] states;

    states = file.getHistory(null);

    for (IFileState state : states) {
      // timestamp = oldJavaFileVersion.getProperty(fileinfo.getClassName());
      String fileStamp = String.valueOf(state.getModificationTime());
      if (fileStamp.equals(timestamp)) {
        return readString(state.getContents());
      }

    }
    System.err.println("can't find file history" + file.toString() + " his:"
        + timestamp);
    return null;
  }

  /**
   * 保存当前执行的数据文件中所有文件的版本信息
   * 
   * @param source
   */
  public void saveJavaFileVersion() {

    File javaVersionFile = new File(ProjectRootWhitProject + "/jacocoData/"
        + rootPath + "_version.properties");
    try {
      FileOutputStream output = new FileOutputStream(javaVersionFile);
      newJavaFileVersion.store(output, "auto write by jacococ-extion");
      output.close();
    } catch (FileNotFoundException e) {
      ConsoleMessage.log(e);
    } catch (IOException e) {
      ConsoleMessage.log(e);
    }
  }

  /**
   * 读取merger运行时候的文件版本号
   */
  public void readFileVersion() {
    File javaVersionFile = new File(ProjectRootWhitProject + "/jacocoData/"
        + rootPath + "_version.properties");
    try {
      FileInputStream input = new FileInputStream(javaVersionFile);
      oldJavaFileVersion = new Properties();
      oldJavaFileVersion.load(input);

      // return new AgentOptions(prop);
    } catch (IOException e) {
      ConsoleMessage.log(e);
    } catch (Exception e) {
      ConsoleMessage.log(e);
    }

  }

  public boolean ExistProjectMergeSession() {

    File javaVersionFile = new File(JacocoData.getJacocoDataDir() + "/"
        + rootPath + "_version.properties");
    if (javaVersionFile.exists()) {
      return true;
    }
    return false;
  }

  public String readString(InputStream inStream) throws IOException {
    ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int len = -1;
    while ((len = inStream.read(buffer)) != -1) {
      outSteam.write(buffer, 0, len);
    }
    outSteam.close();
    inStream.close();
    return new String(outSteam.toByteArray());

  }

}
