import java.security.*
import javax.crypto.*
import javax.crypto.spec.*


def sourceFilePath = args[0]
if (!sourceFilePath) {
    throw new RuntimeException("Failed to read source file")
}
def command = 'decrypt'
if (args.size() >= 2) {
    command = args[1]
}

def secret = System.getenv("SECRET")
if (!secret) {
    println "Enter secret: "
    secret = System.in.newReader().readLine()
    println "Got secret ${'*' * secret.length()}"
}

println "Command: $command"

def cipher = Cipher.getInstance('DES')
def mode = command == 'encrypt' ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE
cipher.init(mode,
    SecretKeyFactory.getInstance('DES')
        .generateSecret(new DESKeySpec(secret.getBytes('UTF-8')))
)

if (command == 'encrypt') {
    File sourceFile = new File(sourceFilePath)
    def source = sourceFile.text

    def checksum = generateMD5(source)
    def target = cipher.doFinal(source.getBytes("UTF-8")).encodeBase64()
    println "Crypted: $target"
    println "Checksum: $checksum"
    File output = new File(sourceFile.parentFile, sourceFile.name + ".secret")
    output.write("$checksum:$target")
    println "Saved to file ${output.absolutePath}"
}
else {
    File sourceFile = new File(sourceFilePath)
    def source = sourceFile.text

    def (checksum, encrypted) = source.split(':')
    if (!checksum || !encrypted) {
        throw new RuntimeException("Wrong input file: checksum or encrypted value is not found")
    }

    def decrypted = new String(cipher.doFinal(encrypted.decodeBase64()))
    def decryptedChecksum = generateMD5(decrypted)
    if (decryptedChecksum != checksum) {
        throw new RuntimeException("Failed to decrypt: checksum do not match")
    }
    println decrypted
}

def generateMD5(String input) {
    MessageDigest digest = MessageDigest.getInstance("MD5")
    digest.update(input.getBytes('UTF-8'))
    byte[] md5sum = digest.digest()
    BigInteger bigInt = new BigInteger(1, md5sum)
    bigInt.toString(16).padLeft(32, '0')
}