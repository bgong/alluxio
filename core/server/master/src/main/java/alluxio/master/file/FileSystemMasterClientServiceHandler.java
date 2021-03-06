/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.master.file;

import alluxio.AlluxioURI;
import alluxio.Constants;
import alluxio.RpcUtils;
import alluxio.RpcUtils.RpcCallable;
import alluxio.RpcUtils.RpcCallableThrowsIOException;
import alluxio.exception.AlluxioException;
import alluxio.master.file.options.CheckConsistencyOptions;
import alluxio.master.file.options.CompleteFileOptions;
import alluxio.master.file.options.CreateDirectoryOptions;
import alluxio.master.file.options.CreateFileOptions;
import alluxio.master.file.options.FreeOptions;
import alluxio.master.file.options.ListStatusOptions;
import alluxio.master.file.options.LoadMetadataOptions;
import alluxio.master.file.options.MountOptions;
import alluxio.master.file.options.RenameOptions;
import alluxio.master.file.options.SetAttributeOptions;
import alluxio.thrift.AlluxioTException;
import alluxio.thrift.CheckConsistencyTOptions;
import alluxio.thrift.CompleteFileTOptions;
import alluxio.thrift.CreateDirectoryTOptions;
import alluxio.thrift.CreateFileTOptions;
import alluxio.thrift.FileBlockInfo;
import alluxio.thrift.FileInfo;
import alluxio.thrift.FileSystemMasterClientService;
import alluxio.thrift.FreeTOptions;
import alluxio.thrift.ListStatusTOptions;
import alluxio.thrift.MountTOptions;
import alluxio.thrift.SetAttributeTOptions;
import alluxio.thrift.ThriftIOException;
import alluxio.wire.ThriftUtils;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * This class is a Thrift handler for file system master RPCs invoked by an Alluxio client.
 */
@NotThreadSafe // TODO(jiri): make thread-safe (c.f. ALLUXIO-1664)
public final class FileSystemMasterClientServiceHandler implements
    FileSystemMasterClientService.Iface {
  private static final Logger LOG =
      LoggerFactory.getLogger(FileSystemMasterClientServiceHandler.class);
  private final FileSystemMaster mFileSystemMaster;

  /**
   * Creates a new instance of {@link FileSystemMasterClientServiceHandler}.
   *
   * @param fileSystemMaster the {@link FileSystemMaster} the handler uses internally
   */
  public FileSystemMasterClientServiceHandler(FileSystemMaster fileSystemMaster) {
    Preconditions.checkNotNull(fileSystemMaster);
    mFileSystemMaster = fileSystemMaster;
  }

  @Override
  public long getServiceVersion() {
    return Constants.FILE_SYSTEM_MASTER_CLIENT_SERVICE_VERSION;
  }

  @Override
  public List<String> checkConsistency(final String path, final CheckConsistencyTOptions options)
      throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter CheckConsistency. path:{}, options:{}", path, options);
    List<String> ret = RpcUtils.call(new RpcCallableThrowsIOException<List<String>>() {
      @Override
      public List<String> call() throws AlluxioException, IOException {
        List<AlluxioURI> inconsistentUris = mFileSystemMaster.checkConsistency(
            new AlluxioURI(path), new CheckConsistencyOptions(options));
        List<String> uris = new ArrayList<>(inconsistentUris.size());
        for (AlluxioURI uri : inconsistentUris) {
          uris.add(uri.getPath());
        }
        return uris;
      }
    });
    LOG.debug("Exit CheckConsistency. path:{}, options:{}", path, options);
    return ret;
  }

  @Override
  public void completeFile(final String path, final CompleteFileTOptions options)
      throws AlluxioTException {
    LOG.debug("Enter CompleteFile. path:{}, options:{}", path, options);
    RpcUtils.call(new RpcCallable<Void>() {
      @Override
      public Void call() throws AlluxioException {
        mFileSystemMaster.completeFile(new AlluxioURI(path), new CompleteFileOptions(options));
        return null;
      }
    });
    LOG.debug("Exit CompleteFile. path:{}, options:{}", path, options);
  }

  @Override
  public void createDirectory(final String path, final CreateDirectoryTOptions options)
      throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter CreateDirectory. path:{}, options:{}", path, options);
    RpcUtils.call(new RpcCallableThrowsIOException<Void>() {
      @Override
      public Void call() throws AlluxioException, IOException {
        mFileSystemMaster.createDirectory(new AlluxioURI(path),
            new CreateDirectoryOptions(options));
        return null;
      }
    });
    LOG.debug("Exit CreateDirectory. path:{}, options:{}", path, options);
  }

  @Override
  public void createFile(final String path, final CreateFileTOptions options)
      throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter CreateFile. path:{}, options:{}", path, options);
    RpcUtils.call(new RpcCallableThrowsIOException<Void>() {
      @Override
      public Void call() throws AlluxioException, IOException {
        mFileSystemMaster.createFile(new AlluxioURI(path), new CreateFileOptions(options));
        return null;
      }
    });
    LOG.debug("Exit CreateFile. path:{}, options:{}", path, options);
  }

  @Override
  public void free(final String path, final boolean recursive, final FreeTOptions options)
      throws AlluxioTException {
    LOG.debug("Enter Free. path:{}, recursive:{}, options:{}", path, recursive, options);
    RpcUtils.call(new RpcCallable<Void>() {
      @Override
      public Void call() throws AlluxioException {
        if (options == null) {
          // For Alluxio client v1.4 or earlier.
          // NOTE, we try to be conservative here so early Alluxio clients will not be able to force
          // freeing pinned items but see the error thrown.
          mFileSystemMaster.free(new AlluxioURI(path),
              FreeOptions.defaults().setRecursive(recursive));
        } else {
          mFileSystemMaster.free(new AlluxioURI(path), new FreeOptions(options));
        }
        return null;
      }
    });
    LOG.debug("Exit Free. path:{}, recursive:{}, options:{}", path, recursive, options);
  }

  /**
   * {@inheritDoc}
   *
   * @deprecated since version 1.1 and will be removed in version 2.0
   * @see #getStatus(String)
   */
  @Override
  @Deprecated
  public List<FileBlockInfo> getFileBlockInfoList(final String path) throws AlluxioTException {
    LOG.debug("Enter GetFileBlockInfoList. path:{}", path);
    List<FileBlockInfo> ret = RpcUtils.call(new RpcCallable<List<FileBlockInfo>>() {
      @Override
      public List<FileBlockInfo> call() throws AlluxioException {
        List<FileBlockInfo> result = new ArrayList<>();
        for (alluxio.wire.FileBlockInfo fileBlockInfo :
            mFileSystemMaster.getFileBlockInfoList(new AlluxioURI(path))) {
          result.add(ThriftUtils.toThrift(fileBlockInfo));
        }
        return result;
      }
    });
    LOG.debug("Exit GetFileBlockInfoList. path:{}", path);
    return ret;
  }

  @Override
  public long getNewBlockIdForFile(final String path) throws AlluxioTException {
    LOG.debug("Enter GetNewBlockIdForFile. path:{}", path);
    long ret = RpcUtils.call(new RpcCallable<Long>() {
      @Override
      public Long call() throws AlluxioException {
        return mFileSystemMaster.getNewBlockIdForFile(new AlluxioURI(path));
      }
    });
    LOG.debug("Exit GetNewBlockIdForFile. path:{}", path);
    return ret;
  }

  @Override
  public FileInfo getStatus(final String path) throws AlluxioTException {
    LOG.debug("Enter GetStatus. path:{}", path);
    FileInfo ret = RpcUtils.call(new RpcCallable<FileInfo>() {
      @Override
      public FileInfo call() throws AlluxioException {
        return ThriftUtils.toThrift(mFileSystemMaster.getFileInfo(new AlluxioURI(path)));
      }
    });
    LOG.debug("Exit GetStatus. path:{}", path);
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * @deprecated since version 1.1 and will be removed in version 2.0
   */
  @Override
  @Deprecated
  public FileInfo getStatusInternal(final long fileId) throws AlluxioTException {
    LOG.debug("Enter GetStatusInternal. fileId:{}", fileId);
    FileInfo ret = RpcUtils.call(new RpcCallable<FileInfo>() {
      @Override
      public FileInfo call() throws AlluxioException {
        return ThriftUtils.toThrift(mFileSystemMaster.getFileInfo(fileId));
      }
    });
    LOG.debug("Exit GetStatusInternal. fileId:{}", fileId);
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * @deprecated since version 1.1 and will be removed in version 2.0
   */
  @Override
  @Deprecated
  public String getUfsAddress() throws AlluxioTException {
    LOG.debug("Enter GetUfsAddress.");
    String ret = RpcUtils.call(new RpcCallable<String>() {
      @Override
      public String call() throws AlluxioException {
        return mFileSystemMaster.getUfsAddress();
      }
    });
    LOG.debug("Exit GetUfsAddress.");
    return ret;
  }

  @Override
  public List<FileInfo> listStatus(final String path, final ListStatusTOptions options)
      throws AlluxioTException {
    LOG.debug("Enter ListStatus. path:{}, options:{}", path, options);
    List<FileInfo> ret = RpcUtils.call(new RpcCallable<List<FileInfo>>() {
      @Override
      public List<FileInfo> call() throws AlluxioException {
        List<FileInfo> result = new ArrayList<>();
        for (alluxio.wire.FileInfo fileInfo : mFileSystemMaster
            .listStatus(new AlluxioURI(path), new ListStatusOptions(options))) {
          result.add(ThriftUtils.toThrift(fileInfo));
        }
        return result;
      }
    });
    LOG.debug("Exit ListStatus. path:{}, options:{}", path, options);
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * @deprecated since version 1.1 and will be removed in version 2.0
   */
  @Override
  @Deprecated
  public long loadMetadata(final String alluxioPath, final boolean recursive)
      throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter LoadMetadata. alluxioPath:{}, recursive:{}", alluxioPath, recursive);
    long ret = RpcUtils.call(new RpcCallableThrowsIOException<Long>() {
      @Override
      public Long call() throws AlluxioException, IOException {
        return mFileSystemMaster.loadMetadata(new AlluxioURI(alluxioPath),
            LoadMetadataOptions.defaults().setCreateAncestors(true).setLoadDirectChildren(true));
      }
    });
    LOG.debug("Exit LoadMetadata. alluxioPath:{}, recursive:{}", alluxioPath, recursive);
    return ret;
  }

  @Override
  public void mount(final String alluxioPath, final String ufsPath, final MountTOptions options)
      throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter Mount. alluxioPath:{}, ufsPath:{}, options:{}", alluxioPath, ufsPath, options);
    RpcUtils.call(new RpcCallableThrowsIOException<Void>() {
      @Override
      public Void call() throws AlluxioException, IOException {
        mFileSystemMaster.mount(new AlluxioURI(alluxioPath), new AlluxioURI(ufsPath),
            new MountOptions(options));
        return null;
      }
    });
    LOG.debug("Exit Mount. alluxioPath:{}, ufsPath:{}, options:{}", alluxioPath, ufsPath, options);
  }

  @Override
  public void remove(final String path, final boolean recursive)
      throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter Remove. path:{}, recursive:{}", path, recursive);
    RpcUtils.call(new RpcCallableThrowsIOException<Void>() {
      @Override
      public Void call() throws AlluxioException, IOException {
        mFileSystemMaster.delete(new AlluxioURI(path), recursive);
        return null;
      }
    });
    LOG.debug("Exit Remove. path:{}, recursive:{}", path, recursive);
  }

  @Override
  public void rename(final String srcPath, final String dstPath)
      throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter Rename. srcPath:{}, dstPath:{}", srcPath, dstPath);
    RpcUtils.call(new RpcCallableThrowsIOException<Void>() {
      @Override
      public Void call() throws AlluxioException, IOException {
        mFileSystemMaster
            .rename(new AlluxioURI(srcPath), new AlluxioURI(dstPath), RenameOptions.defaults());
        return null;
      }
    });
    LOG.debug("Exit Rename. srcPath:{}, dstPath:{}", srcPath, dstPath);
  }

  @Override
  public void scheduleAsyncPersist(final String path) throws AlluxioTException {
    LOG.debug("Enter ScheduleAsyncPersist. path:{}", path);
    RpcUtils.call(new RpcCallable<Void>() {
      @Override
      public Void call() throws AlluxioException {
        mFileSystemMaster.scheduleAsyncPersistence(new AlluxioURI(path));
        return null;
      }
    });
    LOG.debug("Exit ScheduleAsyncPersist. path:{}", path);
  }

  // TODO(calvin): Do not rely on client side options
  @Override
  public void setAttribute(final String path, final SetAttributeTOptions options)
      throws AlluxioTException {
    LOG.debug("Enter SetAttribute. path:{}, options:{}", path, options);
    RpcUtils.call(new RpcCallable<Void>() {
      @Override
      public Void call() throws AlluxioException {
          mFileSystemMaster.setAttribute(new AlluxioURI(path), new SetAttributeOptions(options));
          return null;
      }
    });
    LOG.debug("Exit SetAttribute. path:{}, options:{}", path, options);
  }

  @Override
  public void unmount(final String alluxioPath) throws AlluxioTException, ThriftIOException {
    LOG.debug("Enter Unmount. alluxioPath:{}", alluxioPath);
    RpcUtils.call(new RpcCallableThrowsIOException<Void>() {
      @Override
      public Void call() throws AlluxioException, IOException {
        mFileSystemMaster.unmount(new AlluxioURI(alluxioPath));
        return null;
      }
    });
    LOG.debug("Exit Unmount. alluxioPath:{}", alluxioPath);
  }
}
