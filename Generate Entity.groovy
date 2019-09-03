import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

import java.text.SimpleDateFormat
import java.time.LocalDateTime


//정규식 표현된 매핑 테이블
//표현식 : 매핑 자료형
typeMapping = [
        (~/(?i)tinyint/)                  : "Boolean",
        (~/(?i)mediumint|smallint/)       : "Integer",
        (~/(?i)int/)                      : "Long",
        (~/(?i)float|double|decimal|real/): "Double",
        (~/(?i)timestamp/)                : "Integer",
        (~/(?i)datetime/)                 : "LocalDateTime",
        (~/(?i)date/)                     : "java.sql.DateTime",
        (~/(?i)time/)                     : "java.sql.Time",
        (~/(?i)/)                         : "String"
]

//도메인 패키지 네임
packageName = ""


FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable && it.getKind() == ObjectKind.TABLE }.each { generate(it, dir) }
}

def generate(table, dir) {
    def tableName = table.getName()
    def className = javaName(tableName, true)
    def fields = calcFields(table)
    packageName = getPackageName(dir)
    date = new Date()
    def repPath = "${dir.toString()}\\repository"
    def domainPath = "${dir.toString()}\\domain"

    mkdirs([domainPath, repPath])
    new File(domainPath, className + ".java").withPrintWriter('UTF-8') { out -> generate(out, tableName, className, fields) }
    new File(repPath, className+"DomainRepository.java").withPrintWriter('UTF-8') { out -> genRepository(out, table, className, fields) }
}

def copyright(out) {
    out.println "/* \n" +
            " * Author                    Date                     Description  \n" +
            " * ------------------       --------------            ------------------  \n" +
            " *   joeylee                " + new SimpleDateFormat("yyyy-MM-dd").format(date) + "\n" +
            " */"
}


def generate(out, tableName, className, fields) {
    out.println "package $packageName"+".domain;"
    out.println ""
    copyright(out)
    out.println "import lombok.AccessLevel;"
    out.println "import lombok.Getter;"
    out.println "import lombok.NoArgsConstructor;"
    out.println "import javax.persistence.*;"
    out.println "import java.time.LocalDateTime;"

    out.println "import static javax.persistence.GenerationType.IDENTITY;"
    out.println ""
    out.println "@NoArgsConstructor(access = AccessLevel.PROTECTED)"
    out.println "@Getter"
    out.println "@Entity"
    out.println "@Table(name = \"$tableName\")"
    out.println "public class $className {"
    out.println ""
    fields.each() {
        if (it.annos != "") out.println "  ${it.annos}"
        if (it.name == "idx") {
            out.println "    @Id"
            out.println "    @GeneratedValue(strategy=IDENTITY)"
        }
        out.println "    @Column(name=\"${it.colName}\"${it.notNull?", nullable=${!it.notNull}":""}${it.default?", columnDefinition=\"${it.originType} default ${it.default}\"":""})"
        out.println "    private ${it.type} ${it.name};"
        out.println ""
    }
    out.println "}"
}

def mkdirs(dirs) {
    dirs.forEach {
        def f = new File(it)
        if (!f.exists()) {
            f.mkdirs();
        }
    }
}

def getPackageName(dir) {
    return dir.toString().replaceAll("\\\\", ".").replaceAll("/", ".").replaceAll("^.*src(\\.main\\.java\\.)?", "")
}

//colName : DB 컬럼 이름
//name : 변수 이름
//type : 자료형
//notNull : DB not null 값
//originType : DB 자료형
//default : DB 기본값
//annos : DB 코멘트
def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        fields += [[
                           colName: col.getName(),
                           name : javaName(col.getName(), false),
                           type : typeStr,
                           notNull: col.isNotNull(),
                           originType: col.getDataType(),
                           default: col.getDefault(),
                           annos: col.getComment() ? """
    /**
     * $col.comment
     **/""" : ""]]
    }
}

def javaName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1? s : Case.LOWER.apply(s[0]) + s[1..-1]
}

def genRepository(out, table, className, fields) {
    out.println "package $packageName"+".repository;"
    out.println ""
    copyright(out)
    out.println "import $packageName"+".domain.$className;"
    out.println "import org.springframework.data.jpa.repository.JpaRepository;"
    out.println ""
    out.println "interface ${className}DomainRepository extends JpaRepository<$className, ${fields[0].type}> {"
    out.println ""
    out.println "}"
}
